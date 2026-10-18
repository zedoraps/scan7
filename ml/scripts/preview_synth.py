"""Draw YOLO boxes + class names on random samples of a dataset split, as grid images.

    uv run python scripts/preview_synth.py                      # 4 grids of 3x3 from train
    uv run python scripts/preview_synth.py --split val --grids 2 --full 5

Writes ml/out/synth_preview/<split>_grid_<k>.jpg (and with --full N, N full-size annotated
images <split>_<name>.jpg). Each class has a fixed colour.
"""

from __future__ import annotations

import argparse
import colorsys
import random
from pathlib import Path

import cv2
import numpy as np
import yaml

ML = Path(__file__).resolve().parents[1]
CARDS_CONFIG = ML / "config" / "cards.yaml"
DEFAULT_DATA = ML / "out" / "synth"
DEFAULT_OUT = ML / "out" / "synth_preview"


def class_colors(n: int) -> list[tuple[int, int, int]]:
    """Distinct BGR colours (golden-ratio hue steps, two brightness levels)."""
    out = []
    for i in range(n):
        h = (i * 0.618033988749895) % 1.0
        r, g, b = colorsys.hsv_to_rgb(h, 0.85, 1.0 if i % 2 == 0 else 0.75)
        out.append((int(b * 255), int(g * 255), int(r * 255)))
    return out


def read_labels(path: Path) -> list[tuple[int, float, float, float, float]]:
    if not path.exists():
        return []
    rows = []
    for line in path.read_text().splitlines():
        p = line.split()
        if len(p) == 5:
            rows.append((int(p[0]), *map(float, p[1:])))
    return rows


def annotate(img: np.ndarray, labels, names, colors, scale: float = 1.0) -> np.ndarray:
    """Resize by `scale`, then draw boxes and labels (so text stays readable)."""
    if scale != 1.0:
        img = cv2.resize(img, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
    h, w = img.shape[:2]
    font, fs, th = cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1
    for c, cx, cy, bw, bh in labels:
        x0, y0 = int(round((cx - bw / 2) * w)), int(round((cy - bh / 2) * h))
        x1, y1 = int(round((cx + bw / 2) * w)), int(round((cy + bh / 2) * h))
        col = colors[c % len(colors)]
        cv2.rectangle(img, (x0, y0), (x1, y1), col, 2)
        text = names.get(c, str(c))
        (tw, tht), base = cv2.getTextSize(text, font, fs, th)
        ty = y0 - 3 if y0 - tht - base - 3 >= 0 else y0 + tht + 3
        tx = min(max(0, x0), w - tw - 2)
        cv2.rectangle(img, (tx, ty - tht - 2), (tx + tw + 2, ty + base - 1), col, cv2.FILLED)
        lum = 0.114 * col[0] + 0.587 * col[1] + 0.299 * col[2]
        cv2.putText(img, text, (tx + 1, ty - 1), font, fs, (0, 0, 0) if lum > 140 else (255, 255, 255), th,
                    cv2.LINE_AA)
    return img


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", type=Path, default=DEFAULT_DATA, help="dataset root (with images/ and labels/)")
    ap.add_argument("--split", default="train")
    ap.add_argument("--grids", type=int, default=4, help="number of grid images")
    ap.add_argument("--rows", type=int, default=3)
    ap.add_argument("--cols", type=int, default=3)
    ap.add_argument("--cell", type=int, default=640, help="cell size (px, square)")
    ap.add_argument("--full", type=int, default=0, help="also write N full-size annotated images")
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = ap.parse_args()

    names = dict(enumerate(yaml.safe_load(open(CARDS_CONFIG))["classes"]))
    ds = args.data / "dataset.yaml"
    if ds.exists():
        names = {int(k): v for k, v in yaml.safe_load(open(ds))["names"].items()}
    colors = class_colors(len(names))

    imgs = sorted((args.data / "images" / args.split).glob("*.jpg"))
    if not imgs:
        raise SystemExit(f"no images in {args.data / 'images' / args.split}")
    rng = random.Random(args.seed)
    per = args.rows * args.cols
    sample = rng.sample(imgs, min(len(imgs), per * args.grids + args.full))

    args.out.mkdir(parents=True, exist_ok=True)
    for old in args.out.glob(f"{args.split}_*.jpg"):  # previous previews of this split only
        old.unlink()

    cell = args.cell
    for g in range(args.grids):
        chunk = sample[g * per : (g + 1) * per]
        if not chunk:
            break
        grid = np.full((args.rows * (cell + 24) + 4, args.cols * (cell + 4) + 4, 3), 40, np.uint8)
        for k, p in enumerate(chunk):
            img = cv2.imread(str(p))
            labels = read_labels(args.data / "labels" / args.split / f"{p.stem}.txt")
            s = cell / max(img.shape[:2])
            a = annotate(img, labels, names, colors, s)
            r, c = divmod(k, args.cols)
            y, x = 4 + r * (cell + 24), 4 + c * (cell + 4)
            hh, ww = a.shape[:2]
            oy, ox = y + 20 + (cell - hh) // 2, x + (cell - ww) // 2
            grid[oy : oy + hh, ox : ox + ww] = a
            cv2.putText(grid, f"{p.stem}  ({len(labels)} boxes)", (x + 2, y + 15), cv2.FONT_HERSHEY_SIMPLEX, 0.5,
                        (230, 230, 230), 1, cv2.LINE_AA)
        dst = args.out / f"{args.split}_grid_{g + 1}.jpg"
        cv2.imwrite(str(dst), grid, [cv2.IMWRITE_JPEG_QUALITY, 90])
        print(f"wrote {dst}")
    for p in sample[per * args.grids :]:
        img = cv2.imread(str(p))
        labels = read_labels(args.data / "labels" / args.split / f"{p.stem}.txt")
        dst = args.out / f"{args.split}_{p.stem}.jpg"
        cv2.imwrite(str(dst), annotate(img, labels, names, colors), [cv2.IMWRITE_JPEG_QUALITY, 92])
        print(f"wrote {dst}")


if __name__ == "__main__":
    main()
