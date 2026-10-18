"""Export Label Studio annotations of both projects to a YOLO dataset.

    uv run --project ml python ml/labeling/export_yolo.py [--out ml/out/real] [--copy] [--include-unlabeled]

Output:
    ml/out/real/train/{images,labels}   <- project "scan7 train"
    ml/out/real/test/{images,labels}    <- project "scan7 test"
    ml/out/real/dataset.yaml            (names in cards.yaml order; val = test split)
    ml/out/real/export_raw/*.json       (raw Label Studio JSON exports, for reference)

Class ids come from ml/config/cards.yaml (NOT from Label Studio's own YOLO export).

EXIF orientation: Label Studio's browser UI shows photos with EXIF orientation applied, and
stores boxes as percentages of that displayed (upright) image. Ultralytics also applies EXIF
orientation when loading (cv2.imread / PIL exif_size). To remove any ambiguity, images whose
EXIF orientation is not 1 are written as upright copies with the orientation baked in
(and the tag removed); all other images are symlinked (or copied with --copy).

Only tasks with at least one (non-cancelled) annotation are exported, unless
--include-unlabeled is given (then unlabeled photos get an empty label file = background).
If a task has several annotations, the most recently updated one is used.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
from pathlib import Path

from PIL import Image, ImageOps

from lsutil import DATA_ROOT, FROM_NAME, LS, ML_DIR, PROJECTS, load_classes, task_image_relpath


def exif_orientation(path: Path) -> int:
    with Image.open(path) as im:
        return int(im.getexif().get(0x0112, 1) or 1)


def upright_size(path: Path) -> tuple[int, int]:
    with Image.open(path) as im:
        w, h = im.size
        if exif_orientation(path) in (5, 6, 7, 8):
            w, h = h, w
        return w, h


def place_image(src: Path, dst: Path, copy: bool) -> str:
    if dst.exists() or dst.is_symlink():
        dst.unlink()
    if exif_orientation(src) != 1:
        with Image.open(src) as im:
            up = ImageOps.exif_transpose(im)
            up.save(dst, quality=95, subsampling=0)
        return "baked-exif"
    if copy:
        shutil.copy2(src, dst)
        return "copy"
    dst.symlink_to(os.path.relpath(src, dst.parent))
    return "symlink"


def pick_annotation(task: dict) -> dict | None:
    anns = [a for a in task.get("annotations", []) if not a.get("was_cancelled")]
    if not anns:
        return None
    return max(anns, key=lambda a: a.get("updated_at") or a.get("created_at") or "")


def convert_result(results: list[dict], cls_id: dict[str, int], img_wh, where: str) -> list[str]:
    W, H = img_wh
    lines = []
    for r in results:
        if r.get("type") != "rectanglelabels" or r.get("from_name") != FROM_NAME:
            continue
        v = r["value"]
        labels = v.get("rectanglelabels") or []
        if len(labels) != 1:
            print(f"  WARN {where}: box with labels {labels} skipped (need exactly one)")
            continue
        name = labels[0]
        if name not in cls_id:
            raise SystemExit(f"{where}: unknown label {name!r} (not in cards.yaml)")
        if abs(float(v.get("rotation", 0) or 0)) > 0.01:
            print(f"  WARN {where}: rotated box ({v['rotation']} deg) - using its unrotated extent")
        ow, oh = r.get("original_width"), r.get("original_height")
        if ow and oh and (ow, oh) != (W, H):
            raise SystemExit(
                f"{where}: Label Studio saw the image as {ow}x{oh}, but upright size is {W}x{H}. "
                "EXIF handling mismatch - check the image."
            )
        x0 = max(0.0, v["x"] / 100.0)
        y0 = max(0.0, v["y"] / 100.0)
        x1 = min(1.0, (v["x"] + v["width"]) / 100.0)
        y1 = min(1.0, (v["y"] + v["height"]) / 100.0)
        if x1 <= x0 or y1 <= y0:
            continue
        lines.append(f"{cls_id[name]} {(x0 + x1) / 2:.6f} {(y0 + y1) / 2:.6f} {x1 - x0:.6f} {y1 - y0:.6f}")
    return lines


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=ML_DIR / "out" / "real")
    ap.add_argument("--copy", action="store_true", help="copy images instead of symlinking")
    ap.add_argument("--include-unlabeled", action="store_true")
    args = ap.parse_args()
    out: Path = args.out.resolve()

    classes = load_classes()
    cls_id = {c: i for i, c in enumerate(classes)}
    ls = LS()
    (out / "export_raw").mkdir(parents=True, exist_ok=True)

    for split, (title, _folder) in PROJECTS.items():
        p = ls.project_by_title(title)
        if p is None:
            raise SystemExit(f"project {title!r} not found - run setup_projects.py first")
        tasks = ls.get(f"/api/projects/{p['id']}/export", exportType="JSON", download_all_tasks="true")
        (out / "export_raw" / f"{split}.json").write_text(json.dumps(tasks, indent=1))

        img_dir, lbl_dir = out / split / "images", out / split / "labels"
        for d in (img_dir, lbl_dir):  # start fresh so deleted annotations disappear
            if d.exists():
                shutil.rmtree(d)
            d.mkdir(parents=True)

        n_img = n_box = n_skip = 0
        modes: dict[str, int] = {}
        for t in tasks:
            rel = task_image_relpath(t)
            src = (DATA_ROOT / rel).resolve()
            ann = pick_annotation(t)
            if ann is None and not args.include_unlabeled:
                n_skip += 1
                continue
            if not src.exists():
                print(f"  WARN missing image {src}, skipped")
                continue
            lines = convert_result(ann["result"] if ann else [], cls_id, upright_size(src), f"{split}/{rel}")
            dst = img_dir / src.name
            if dst.exists() or dst.is_symlink():
                raise SystemExit(f"duplicate file name {src.name} in {split}")
            mode = place_image(src, dst, args.copy)
            modes[mode] = modes.get(mode, 0) + 1
            (lbl_dir / (src.stem + ".txt")).write_text("\n".join(lines) + ("\n" if lines else ""))
            n_img += 1
            n_box += len(lines)
        print(f"[{split}] {n_img} images, {n_box} boxes exported, {n_skip} unlabeled skipped; images: {modes}")

    names = "\n".join(f"  {i}: '{c}'" for i, c in enumerate(classes))
    (out / "dataset.yaml").write_text(
        "# Generated by ml/labeling/export_yolo.py - real photos labeled in Label Studio.\n"
        "# The test split is the fixed real test set: never train on it.\n"
        f"path: {out}\n"
        "train: train/images\n"
        "val: test/images\n"
        "test: test/images\n"
        f"nc: {len(classes)}\n"
        f"names:\n{names}\n"
    )
    print(f"Wrote {out / 'dataset.yaml'}")


if __name__ == "__main__":
    main()
