"""Run a trained detector on a folder of photos: YOLO txt predictions (with confidence) + preview grids.

Example:
    uv run python scripts/predict.py runs/synth_v1/weights/last.pt data/real_photos/train --out out/pred/v1/train

Writes <out>/labels/<stem>.txt (`cls xc yc w h conf`, upright image coords - the format
ml/labeling/import_predictions.py expects) and <out>/grid_*.jpg previews with boxes drawn.
"""
import argparse
import os
from pathlib import Path

os.environ.setdefault("PYTORCH_ENABLE_MPS_FALLBACK", "1")

import cv2  # noqa: E402
import numpy as np  # noqa: E402
from ultralytics import YOLO  # noqa: E402


def grid(images, cols=4, cell=480):
    tiles = []
    for im in images:
        h, w = im.shape[:2]
        s = cell / max(h, w)
        t = cv2.resize(im, (int(w * s), int(h * s)), interpolation=cv2.INTER_AREA)
        tile = np.full((cell, cell, 3), 32, np.uint8)
        tile[: t.shape[0], : t.shape[1]] = t
        tiles.append(tile)
    while len(tiles) % cols:
        tiles.append(np.full((cell, cell, 3), 32, np.uint8))
    return np.vstack([np.hstack(tiles[r : r + cols]) for r in range(0, len(tiles), cols)])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("weights")
    ap.add_argument("source", help="folder of images")
    ap.add_argument("--out", required=True)
    ap.add_argument("--conf", type=float, default=0.25)
    ap.add_argument("--imgsz", type=int, default=640)
    ap.add_argument("--device", default="mps")
    ap.add_argument("--per-grid", type=int, default=12)
    args = ap.parse_args()

    out = Path(args.out)
    (out / "labels").mkdir(parents=True, exist_ok=True)
    model = YOLO(args.weights)
    files = sorted(p for p in Path(args.source).iterdir() if p.suffix.lower() in {".jpg", ".jpeg", ".png"})

    previews, n_boxes = [], 0
    for f in files:
        r = model.predict(str(f), imgsz=args.imgsz, conf=args.conf, device=args.device, verbose=False)[0]
        lines = [
            f"{int(c)} {x:.6f} {y:.6f} {w:.6f} {h:.6f} {s:.4f}"
            for c, (x, y, w, h), s in zip(r.boxes.cls.tolist(), r.boxes.xywhn.tolist(), r.boxes.conf.tolist())
        ]
        (out / "labels" / f"{f.stem}.txt").write_text("\n".join(lines) + ("\n" if lines else ""))
        n_boxes += len(lines)
        im = r.plot(line_width=3, font_size=28)
        cv2.putText(im, f.stem[-12:], (10, im.shape[0] - 20), cv2.FONT_HERSHEY_SIMPLEX, 2.0, (0, 255, 255), 4)
        previews.append(im)

    for i in range(0, len(previews), args.per_grid):
        cv2.imwrite(str(out / f"grid_{i // args.per_grid}.jpg"), grid(previews[i : i + args.per_grid]),
                    [cv2.IMWRITE_JPEG_QUALITY, 85])
    print(f"{len(files)} images, {n_boxes} boxes -> {out}")


if __name__ == "__main__":
    main()
