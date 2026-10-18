"""Upload YOLO-format predictions to Label Studio as pre-labels (predictions), for faster labeling.

    uv run --project ml python ml/labeling/import_predictions.py PRED_LABELS_DIR \
        [--images ml/data/real_photos/train] [--project train] [--model-version v1] [--replace]

PRED_LABELS_DIR holds one <image stem>.txt per image, lines `cls xc yc w h [conf]` with
normalized coordinates relative to the upright (EXIF-applied) image - which is what
Ultralytics writes with `yolo predict ... save_txt=True save_conf=True` (labels/ subfolder).
Class ids follow ml/config/cards.yaml.

Each txt is matched to the Label Studio task whose image has the same file stem. Tasks that
already have a prediction with the same --model-version are skipped unless --replace is given.
In the UI, open a task and the prediction appears as an editable draft; correct it and Submit.
Default project is "train" - never pre-label the test project for evaluation purposes with
a model you later compare against (it biases the labels towards the model).
"""

from __future__ import annotations

import argparse
from pathlib import Path

from export_yolo import upright_size
from lsutil import DATA_ROOT, FROM_NAME, LS, PROJECTS, TO_NAME, load_classes, task_image_relpath


def yolo_to_result(lines: list[str], classes: list[str], wh) -> tuple[list[dict], float | None]:
    W, H = wh
    result, confs = [], []
    for i, line in enumerate(lines):
        parts = line.split()
        if not parts:
            continue
        c = int(float(parts[0]))
        xc, yc, w, h = map(float, parts[1:5])
        conf = float(parts[5]) if len(parts) > 5 else None
        if conf is not None:
            confs.append(conf)
        r = {
            "id": f"pred{i}",
            "from_name": FROM_NAME,
            "to_name": TO_NAME,
            "type": "rectanglelabels",
            "original_width": W,
            "original_height": H,
            "image_rotation": 0,
            "value": {
                "x": (xc - w / 2) * 100.0,
                "y": (yc - h / 2) * 100.0,
                "width": w * 100.0,
                "height": h * 100.0,
                "rotation": 0,
                "rectanglelabels": [classes[c]],
            },
        }
        if conf is not None:
            r["score"] = conf
        result.append(r)
    return result, (sum(confs) / len(confs) if confs else None)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("pred_dir", type=Path, help="directory of YOLO prediction .txt files")
    ap.add_argument("--images", type=Path, default=None,
                    help="image dir (default: the project's photo folder); used for image sizes")
    ap.add_argument("--project", choices=list(PROJECTS), default="train")
    ap.add_argument("--model-version", default="yolo")
    ap.add_argument("--replace", action="store_true", help="replace existing predictions of this model version")
    ap.add_argument("--min-conf", type=float, default=0.0, help="drop boxes below this confidence")
    args = ap.parse_args()

    classes = load_classes()
    title, folder = PROJECTS[args.project]
    img_dir = (args.images or folder).resolve()
    ls = LS()
    p = ls.project_by_title(title)
    if p is None:
        raise SystemExit(f"project {title!r} not found - run setup_projects.py first")

    tasks = {Path(task_image_relpath(t)).stem: t for t in ls.tasks(p["id"])}
    txts = sorted(args.pred_dir.glob("*.txt"))
    if not txts:
        raise SystemExit(f"no .txt files in {args.pred_dir}")

    n_up = n_skip = n_missing = 0
    for txt in txts:
        t = tasks.get(txt.stem)
        if t is None:
            n_missing += 1
            print(f"  no task for {txt.name}")
            continue
        img = next((q for q in (img_dir / (txt.stem + e) for e in (".jpg", ".jpeg", ".png", ".JPG", ".JPEG"))
                    if q.exists()), None) or (DATA_ROOT / task_image_relpath(t))
        lines = [l for l in txt.read_text().splitlines() if l.strip()]
        if args.min_conf > 0:
            lines = [l for l in lines if len(l.split()) < 6 or float(l.split()[5]) >= args.min_conf]
        bad = [l for l in lines if not 0 <= int(float(l.split()[0])) < len(classes)]
        if bad:
            raise SystemExit(f"{txt}: class id out of range: {bad[0]}")

        existing = [pr for pr in ls.get("/api/predictions", task=t["id"])
                    if pr.get("model_version") == args.model_version]
        if existing and not args.replace:
            n_skip += 1
            continue
        for pr in existing:
            ls.delete(f"/api/predictions/{pr['id']}")

        result, score = yolo_to_result(lines, classes, upright_size(img))
        body = {"task": t["id"], "result": result, "model_version": args.model_version}
        if score is not None:
            body["score"] = score
        ls.post("/api/predictions", body)
        n_up += 1

    print(f"[{title}] uploaded {n_up} predictions, skipped {n_skip} (already present), "
          f"{n_missing} txt files without matching task")


if __name__ == "__main__":
    main()
