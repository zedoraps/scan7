"""AI-assisted labeling: render review sheets for unlabeled Label Studio tasks, then upload the decisions.

    uv run --project ml python ml/labeling/ai_review.py prepare --split train   # -> ml/out/ai_review/train/
    uv run --project ml python ml/labeling/ai_review.py upload  --split train   # reads decisions.json

prepare: for every task in the project WITHOUT an annotation, run the newest model at a low threshold
(default 0.15, so few cards are missed) and write per image:
  <stem>_overview.jpg  the photo with numbered boxes and a 10 % grid (labels 0-9 along the edges)
  <stem>_crops.jpg     enlarged crop of every box: "#3 plus6 0.72"
and proposals.json {stem: {"width", "height", "boxes": [{"i", "label", "score", "box": [l, t, r, b]}]}}
(boxes normalized 0-1 in the upright image).

The reviewer writes decisions.json:
  {stem: {"keep": {"<i>": "<label>", ...},          # every box to keep, with its (corrected) class
          "add":  [{"label": "07", "box": [l, t, r, b]}],   # missed cards, approximate boxes
          "note": "optional free text"}}
Boxes not listed in "keep" are dropped. Class names as in ml/config/cards.yaml.

upload: posts each decision as a Label Studio annotation with the hint "Labeled by Claude (AI) –
please spot-check" (+ note), so the user can review instead of label. Tasks that already have an
annotation are skipped.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

os.environ.setdefault("PYTORCH_ENABLE_MPS_FALLBACK", "1")

import cv2  # noqa: E402
import numpy as np  # noqa: E402

sys.path.insert(0, str(Path(__file__).resolve().parent))
from lsutil import DATA_ROOT, FROM_NAME, HINT_NAME, LS, ML_DIR, PROJECTS, TO_NAME, load_classes, task_image_relpath  # noqa: E402

OUT = ML_DIR / "out" / "ai_review"
AI_HINT = "Labeled by Claude (AI) – please spot-check."


def newest_weights() -> Path:
    cands = sorted((ML_DIR / "runs").glob("*/weights/best.pt"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not cands:
        sys.exit("no weights in ml/runs/*/weights/best.pt")
    return cands[0]


def unlabeled_tasks(ls: LS, split: str) -> list[dict]:
    p = ls.project_by_title(PROJECTS[split][0])
    return [t for t in ls.tasks(p["id"]) if not t.get("total_annotations")]


def overview(img, boxes):
    h, w = img.shape[:2]
    s = 1600 / max(h, w)
    im = cv2.resize(img, (int(w * s), int(h * s)), interpolation=cv2.INTER_AREA)
    H, W = im.shape[:2]
    for k in range(1, 10):  # 10 % grid with edge labels
        x, y = int(W * k / 10), int(H * k / 10)
        cv2.line(im, (x, 0), (x, H), (90, 90, 90), 1)
        cv2.line(im, (0, y), (W, y), (90, 90, 90), 1)
        cv2.putText(im, str(k), (x + 3, 22), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
        cv2.putText(im, str(k), (3, y - 4), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
    for b in boxes:
        l, t, r, bt = b["box"]
        p1, p2 = (int(l * W), int(t * H)), (int(r * W), int(bt * H))
        cv2.rectangle(im, p1, p2, (255, 0, 255), 3)
        cv2.putText(im, f"#{b['i']}", (p1[0] + 4, p1[1] + 34), cv2.FONT_HERSHEY_SIMPLEX, 1.1, (0, 0, 0), 6)
        cv2.putText(im, f"#{b['i']}", (p1[0] + 4, p1[1] + 34), cv2.FONT_HERSHEY_SIMPLEX, 1.1, (255, 255, 255), 2)
    return im


def crops(img, boxes, cell=260, cols=5):
    h, w = img.shape[:2]
    tiles = []
    for b in boxes:
        l, t, r, bt = b["box"]
        pw, ph = (r - l) * 0.12, (bt - t) * 0.12
        x0, y0 = max(0, int((l - pw) * w)), max(0, int((t - ph) * h))
        x1, y1 = min(w, int((r + pw) * w)), min(h, int((bt + ph) * h))
        c = img[y0:y1, x0:x1]
        tile = np.full((cell + 34, cell, 3), 30, np.uint8)
        if c.size:
            s = cell / max(c.shape[:2])
            c = cv2.resize(c, (max(1, int(c.shape[1] * s)), max(1, int(c.shape[0] * s))))
            tile[34:34 + c.shape[0], :c.shape[1]] = c
        cv2.putText(tile, f"#{b['i']} {b['label']} {b['score']:.2f}", (4, 24), cv2.FONT_HERSHEY_SIMPLEX, 0.7,
                    (0, 255, 255), 2)
        tiles.append(tile)
    if not tiles:
        return np.full((60, 400, 3), 30, np.uint8)
    while len(tiles) % cols:
        tiles.append(np.full_like(tiles[0], 30))
    return np.vstack([np.hstack(tiles[i:i + cols]) for i in range(0, len(tiles), cols)])


def prepare(split: str, conf: float, weights: Path | None):
    from ultralytics import YOLO

    ls = LS()
    tasks = unlabeled_tasks(ls, split)
    out = OUT / split
    out.mkdir(parents=True, exist_ok=True)
    model = YOLO(str(weights or newest_weights()))
    names = model.names
    proposals = {}
    for t in tasks:
        path = DATA_ROOT / task_image_relpath(t)
        img = cv2.imread(str(path))  # EXIF applied, same as export_yolo / Label Studio
        h, w = img.shape[:2]
        r = model.predict(str(path), imgsz=640, conf=conf, device="cpu", verbose=False)[0]
        boxes = [{"i": i, "label": names[int(c)], "score": round(float(s), 3),
                  "box": [round(v, 4) for v in b]}
                 for i, (c, s, b) in enumerate(zip(r.boxes.cls.tolist(), r.boxes.conf.tolist(),
                                                   r.boxes.xyxyn.tolist()))]
        proposals[path.stem] = {"width": w, "height": h, "boxes": boxes, "task": t["id"]}
        cv2.imwrite(str(out / f"{path.stem}_overview.jpg"), overview(img, boxes), [cv2.IMWRITE_JPEG_QUALITY, 85])
        cv2.imwrite(str(out / f"{path.stem}_crops.jpg"), crops(img, boxes), [cv2.IMWRITE_JPEG_QUALITY, 88])
    (out / "proposals.json").write_text(json.dumps(proposals, indent=1))
    print(f"[{split}] {len(tasks)} unlabeled tasks -> {out} (model {weights or newest_weights()}, conf {conf})")


def upload(split: str):
    out = OUT / split
    proposals = json.loads((out / "proposals.json").read_text())
    decisions = json.loads((out / "decisions.json").read_text())
    classes = set(load_classes())
    ls = LS()
    done = n_boxes = n_added = 0
    for stem, d in decisions.items():
        p = proposals[stem]
        t = ls.get(f"/api/tasks/{p['task']}")
        if t.get("total_annotations"):
            continue
        by_i = {b["i"]: b for b in p["boxes"]}
        boxes = [(lab, by_i[int(i)]["box"]) for i, lab in d.get("keep", {}).items()]
        boxes += [(a["label"], a["box"]) for a in d.get("add", [])]
        n_added += len(d.get("add", []))
        bad = [lab for lab, _ in boxes if lab not in classes]
        if bad:
            sys.exit(f"{stem}: unknown classes {bad}")
        res = [{"id": f"ai{k}", "from_name": FROM_NAME, "to_name": TO_NAME, "type": "rectanglelabels",
                "original_width": p["width"], "original_height": p["height"], "image_rotation": 0,
                "value": {"x": l * 100, "y": tp * 100, "width": (r - l) * 100, "height": (b - tp) * 100,
                          "rotation": 0, "rectanglelabels": [lab]}}
               for k, (lab, (l, tp, r, b)) in enumerate(boxes)]
        hint = AI_HINT + (f" {d['note']}" if d.get("note") else "")
        res.append({"id": "aihint", "from_name": HINT_NAME, "to_name": TO_NAME, "type": "textarea",
                    "value": {"text": [hint]}})
        ls.post(f"/api/tasks/{p['task']}/annotations", {"result": res})
        done += 1
        n_boxes += len(boxes)
    print(f"[{split}] uploaded {done} annotations ({n_boxes} boxes, {n_added} added by hand)")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cmd", choices=["prepare", "upload"])
    ap.add_argument("--split", choices=list(PROJECTS), required=True)
    ap.add_argument("--conf", type=float, default=0.15)
    ap.add_argument("--weights", type=Path)
    a = ap.parse_args()
    prepare(a.split, a.conf, a.weights) if a.cmd == "prepare" else upload(a.split)


if __name__ == "__main__":
    main()
