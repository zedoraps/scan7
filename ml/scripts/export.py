# /// script
# requires-python = ">=3.12,<3.13"
# dependencies = [
#     "ultralytics==8.4.182",
#     "litert-torch>=0.9.0",
#     "ai-edge-litert>=2.1.4",
#     "ai-edge-quantizer>=0.6.0",
#     "pyyaml",
# ]
# ///
"""Export a trained detector to LiteRT (.tflite) and install it into the Android app.

Runs in its OWN uv environment (PEP 723 header above), not ml/.venv: litert-torch caps
torch<2.14, and the training env uses torch 2.14. So call it with `uv run scripts/export.py`
(NOT `uv run python scripts/export.py`):

    cd ml
    uv run scripts/export.py runs/synth_v1/weights/last.pt
    uv run scripts/export.py runs/synth_v1/weights/best.pt --no-install   # export + verify only

Steps:
 1. Ultralytics `format="litert"` export (litert-torch) on CPU at --imgsz. The model keeps its
    end-to-end (NMS-free) head, so the graph emits final detections.
    Precision: FP32 graph. Ultralytics' LiteRT exporter does not write FP16 files; the LiteRT GPU
    accelerator runs FP32 models in FP16 by default. INT8 (static, calibrated) is planned:
    see `--int8` (not implemented yet; note Ultralytics drops the end2end head for INT8, so the app
    would then need its own NMS path).
 2. Print the input/output tensor details.
 3. Verify: run the PyTorch checkpoint and the .tflite (with the app's preprocessing: centered
    letterbox, pad 114, RGB 0-1, NCHW) on --verify-count real photos and compare boxes.
 4. Copy to app/app/src/main/assets/model.tflite, write labels.txt (cards.yaml class order) and
    model_version.txt (one line, shown in the app footer).
"""
import argparse
import datetime as dt
import os
import shutil
import sys
from pathlib import Path

import numpy as np
import yaml

ML = Path(__file__).resolve().parent.parent
REPO = ML.parent
ASSETS = REPO / "app/app/src/main/assets"
PAD_VALUE = 114


def letterbox(img_rgb: np.ndarray, size: int):
    """Centered letterbox, same math as the app's Letterbox.kt. Returns (canvas, scale, pad_x, pad_y)."""
    import cv2

    h, w = img_rgb.shape[:2]
    scale = min(size / w, size / h)
    nw, nh = round(w * scale), round(h * scale)
    pad_x, pad_y = (size - nw) / 2, (size - nh) / 2
    resized = cv2.resize(img_rgb, (nw, nh), interpolation=cv2.INTER_LINEAR)
    canvas = np.full((size, size, 3), PAD_VALUE, np.uint8)
    x0, y0 = int(round(pad_x - 0.1)), int(round(pad_y - 0.1))
    canvas[y0 : y0 + nh, x0 : x0 + nw] = resized
    return canvas, scale, x0, y0


def tflite_predict(interp, img_bgr, size, conf):
    """Run the .tflite on one image; returns [(cls, score, x1, y1, x2, y2)] in original pixels."""
    rgb = img_bgr[:, :, ::-1]
    canvas, scale, px, py = letterbox(rgb, size)
    inp = interp.get_input_details()[0]
    x = canvas.astype(np.float32) / 255.0
    x = x.transpose(2, 0, 1)[None] if inp["shape"][1] == 3 else x[None]
    interp.set_tensor(inp["index"], x)
    interp.invoke()
    out = interp.get_tensor(interp.get_output_details()[0]["index"])[0]  # [N, 6]
    dets = []
    for x1, y1, x2, y2, s, c in out:
        if s < conf:
            continue
        dets.append((int(c), float(s), (x1 - px) / scale, (y1 - py) / scale, (x2 - px) / scale, (y2 - py) / scale))
    return dets


def iou(a, b):
    ix = max(0.0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0.0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return inter / union if union > 0 else 0.0


def verify(model, tflite_path, photos, size, conf):
    """Compare PyTorch vs LiteRT detections. Returns True if every box matches (same class, IoU>=0.9)."""
    import cv2
    from ai_edge_litert.interpreter import Interpreter

    interp = Interpreter(model_path=str(tflite_path))
    interp.allocate_tensors()
    ok = True
    for f in photos:
        img = cv2.imread(str(f))  # Ultralytics reads with EXIF orientation ignored too (cv2) - same input
        # nms=False: use the same end-to-end (one-to-one) head as the export. The 8.4 default (nms=None) runs the
        # one-to-many head + NMS, which is a different head and scores differently (esp. early in training).
        r = model.predict(img, imgsz=size, conf=conf, device="cpu", nms=False, verbose=False)[0]
        pt = [(int(c), float(s), *b) for c, s, b in zip(r.boxes.cls.tolist(), r.boxes.conf.tolist(), r.boxes.xyxy.tolist())]
        tf = tflite_predict(interp, img, size, conf)
        used, worst_iou, worst_ds, unmatched = set(), 1.0, 0.0, 0
        for p in pt:
            best, bi = 0.0, None
            for i, t in enumerate(tf):
                if i in used or t[0] != p[0]:
                    continue
                v = iou(p[2:], t[2:])
                if v > best:
                    best, bi = v, i
            if bi is None or best < 0.9:
                unmatched += 1
                continue
            used.add(bi)
            worst_iou = min(worst_iou, best)
            worst_ds = max(worst_ds, abs(p[1] - tf[bi][1]))
        extra = len(tf) - len(used)
        # Boxes right at the threshold may flip sides; only count misses for clearly-above-threshold boxes.
        borderline = sum(1 for d in pt + tf if abs(d[1] - conf) < 0.02)
        match = unmatched == 0 and extra == 0
        ok &= match or (unmatched + extra) <= borderline
        print(f"  {f.name}: pytorch {len(pt)} boxes, tflite {len(tf)} boxes, unmatched {unmatched}, extra {extra}, "
              f"min IoU {worst_iou:.4f}, max |dscore| {worst_ds:.4f}" + ("" if match else f" (borderline {borderline})"))
        for p in sorted(pt, key=lambda d: -d[1])[:5]:
            print(f"     pt  cls {p[0]:2d} {p[1]:.3f} box [{p[2]:.0f} {p[3]:.0f} {p[4]:.0f} {p[5]:.0f}]")
        for t in sorted(tf, key=lambda d: -d[1])[:5]:
            print(f"     tfl cls {t[0]:2d} {t[1]:.3f} box [{t[2]:.0f} {t[3]:.0f} {t[4]:.0f} {t[5]:.0f}]")
    return ok


def describe(tflite_path):
    from ai_edge_litert.interpreter import Interpreter

    interp = Interpreter(model_path=str(tflite_path))
    interp.allocate_tensors()
    for kind, details in (("input", interp.get_input_details()), ("output", interp.get_output_details())):
        for d in details:
            q = d.get("quantization")
            print(f"  {kind:6s} {d['name']}: shape {list(d['shape'])} dtype {np.dtype(d['dtype']).name} quant {q}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("weights")
    ap.add_argument("--imgsz", type=int, default=640)
    ap.add_argument("--int8", action="store_true", help="static INT8 with calibration (milestone 5, not implemented)")
    ap.add_argument("--calib-data", help="dataset yaml for --int8 calibration")
    ap.add_argument("--verify-dir", default=str(ML / "data/real_photos/train"))
    ap.add_argument("--verify-count", type=int, default=3)
    ap.add_argument("--conf", type=float, default=0.25)
    ap.add_argument("--no-install", action="store_true", help="don't copy into the app assets")
    args = ap.parse_args()

    if args.int8:
        # Planned: YOLO(...).export(format="litert", quantize=8, data=args.calib_data). Ultralytics then drops the
        # end2end head (raw [1, 4+nc, anchors] output) so the app needs an NMS path; keep this explicit for now.
        sys.exit("--int8 is not implemented yet (milestone 5).")

    os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")
    import torch
    from ultralytics import YOLO

    torch.set_num_threads(max(1, (os.cpu_count() or 4) // 2))  # leave CPU for the running training
    weights = Path(args.weights).resolve()
    classes = yaml.safe_load((ML / "config/cards.yaml").read_text())["classes"]
    model = YOLO(str(weights))
    names = [model.names[i] for i in range(len(model.names))]
    if names != classes:
        sys.exit(f"Checkpoint class names {names} differ from cards.yaml {classes}")
    print(f"torch {torch.__version__}; checkpoint {weights} (epoch {model.ckpt.get('epoch')})")

    # Export from a copy so the .tflite lands in a scratch dir, never next to the (possibly live) checkpoint.
    work = ML / "out/export" / f"{weights.parent.parent.name}_{weights.stem}"
    work.mkdir(parents=True, exist_ok=True)
    src = work / f"{weights.stem}.pt"
    shutil.copy2(weights, src)
    out = YOLO(str(src)).export(
        format="litert", imgsz=args.imgsz, device="cpu", batch=1,
        nms=False,  # 8.4 default nms=None means "external NMS" (raw [1, 4+nc, 8400]); False keeps the NMS-free head
    )
    tflite = Path(out)
    print(f"\nExported {tflite} ({tflite.stat().st_size / 1e6:.2f} MB)")
    describe(tflite)

    photos = sorted(p for p in Path(args.verify_dir).glob("*.jpg"))[: args.verify_count]
    print(f"\nVerifying PyTorch vs LiteRT on {len(photos)} photos (conf {args.conf}):")
    ok = verify(model, tflite, photos, args.imgsz, args.conf)
    print("verification:", "PASS" if ok else "FAIL")
    if not ok:
        sys.exit(1)

    if not args.no_install:
        ASSETS.mkdir(parents=True, exist_ok=True)
        shutil.copy2(tflite, ASSETS / "model.tflite")
        (ASSETS / "labels.txt").write_text("\n".join(classes) + "\n")
        epoch = model.ckpt.get("epoch")
        version = (f"{weights.parent.parent.name}/{weights.stem}"
                   + (f" ep{epoch}" if epoch is not None and epoch >= 0 else "")
                   + f" · fp32 · {args.imgsz}px · {dt.date.today().isoformat()}")
        (ASSETS / "model_version.txt").write_text(version + "\n")
        print(f"Installed into {ASSETS}: model.tflite, labels.txt, model_version.txt ({version})")


if __name__ == "__main__":
    main()
