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
    uv run scripts/export.py runs/veng_synth_v1/weights/best.pt --imgsz 800 --cards config/cards_vengeance.yaml \
        --asset-dir vengeance --verify-dir data/vengeance_raw   # With a Vengeance: assets/vengeance/
    uv run scripts/export.py runs/ft_v1/weights/best.pt --int8 static_e2e --no-install
    uv run scripts/export.py runs/ft_v1/weights/best.pt --int8 all   # all variants (evaluate: scripts/eval_tflite.py)

Steps:
 1. Ultralytics `format="litert"` export (litert-torch) on CPU at --imgsz. The model keeps its
    end-to-end (NMS-free) head, so the graph emits final detections.
    Precision: FP32 graph. Ultralytics' LiteRT exporter does not write FP16 files; the LiteRT GPU
    accelerator runs FP32 models in FP16 by default.
 1b. --int8 SCHEME: post-training quantization of that FP32 .tflite with ai-edge-quantizer (float32
    input/output kept, so the app contract - NCHW float in, [1, 300, 6] float out - is unchanged
    for every scheme except `ultralytics`). Static schemes calibrate on --calib-count images
    (all of out/real/train, topped up from out/synth val; never a test set) letterboxed like the app.
    Schemes:
      dynamic         int8 weights, float activations quantized on the fly (dynamic-range)
      weight_only     int8 weights dequantized to float at load, float compute
      static          full integer: int8 weights + int8 activations, including the head's decode /
                      top-k (collapses the score/class output, see INT8_REPORT.md)
      static_e2e      full integer except the head's six final 1x1 prediction convs (box + class
                      logits per scale) and the end-to-end post-processing (box decode, sigmoid,
                      top-k, gather, output concat), which stay float -> keeps [1, 300, 6]
      static_head     int8 backbone + neck; the whole Detect head (its convs too) stays float
      w8a16           int8 weights + int16 activations (whole graph)
      ultralytics     Ultralytics' own quantize=8 (drops the end2end head: raw [1, 4+nc, 8400]
                      output with normalized xywh, needs NMS in the app; evaluation only)
      all             every scheme above (implies --no-install)
    The quantized files are written next to the FP32 one as <stem>_<scheme>.tflite.
 2. Print the input/output tensor details.
 3. Verify: run the PyTorch checkpoint and the .tflite (with the app's preprocessing: centered
    letterbox, pad 114, RGB 0-1, NCHW) on --verify-count real photos and compare boxes.
 4. Copy (the --int8 file if given) to app/app/src/main/assets/[<asset-dir>/]model.tflite, write labels.txt (--cards class order) and
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
INT8_SCHEMES = ("dynamic", "weight_only", "static", "static_e2e", "static_head", "w8a16", "ultralytics")
# Tensor names (op outputs) in the litert-torch graph. ai-edge-quantizer matches the regex against the op "scope" =
# its output tensor names joined by ";" plus a trailing ";". Kept float by static_e2e:
#  - the Detect module's own ops ("...head.Detect_23;NN": decode, sigmoid, top-k, gather) and the output concat;
#    quantizing those collapses the [1, 300, 6] rows (scores/class ids share one int8 scale with pixel boxes);
#  - the six final prediction convs ".../head.Detect_23/...Sequential_i/torch.nn.modules.conv.Conv2d_2;N" (and their
#    transposes): int8 class logits lowered every score by ~0.25-0.32 on average (ft_v1/ft_v3) - kept float, the
#    mean score shift is ~-0.02. The rest of the head (its 3x3 / depthwise towers) is int8.
E2E_POSTPROCESS_REGEX = (r"head\.Detect_\d+;\d+;|^serving_default_output"
                         r"|head\.Detect_\d+/torch\.nn\.modules\.container\.Sequential_\d+/torch\.nn\.modules\.conv\.Conv2d_2;")
DETECT_HEAD_REGEX = r"head\.Detect_\d+|^serving_default_output"


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


def preprocess(img_bgr, size, nchw=True):
    """App preprocessing: centered letterbox, RGB 0-1 float, NCHW. Returns (x[1,3,S,S], scale, pad_x, pad_y)."""
    canvas, scale, px, py = letterbox(img_bgr[:, :, ::-1], size)
    x = canvas.astype(np.float32) / 255.0
    return (x.transpose(2, 0, 1)[None] if nchw else x[None]), scale, px, py


def tflite_predict(interp, img_bgr, size, conf):
    """Run the .tflite on one image; returns [(cls, score, x1, y1, x2, y2)] in original pixels."""
    inp = interp.get_input_details()[0]
    x, scale, px, py = preprocess(img_bgr, size, nchw=inp["shape"][1] == 3)
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


def calibration_images(size, count, seed=0):
    """`count` calibration inputs (app preprocessing): every real train photo (up to `count`), topped up with
    synthetic val images.

    Real photos come first on purpose: with a 50/50 real/synthetic mix the synthetic images' activation ranges
    cost ft_v3 @800 px 14 points of recall in `static_e2e` (R 0.716 vs 0.852 FP32); real-first calibration
    brought it back to 0.833 (see out/export/INT8_REPORT.md).
    Never the real test set (out/real/test, data/real_photos/test) nor the web test set.
    """
    import random

    import cv2

    real = sorted((ML / "out/real/train/images").glob("*.jpg"))
    synth = sorted((ML / "out/synth/images/val").glob("*.jpg"))
    for f in real + synth:
        assert "test" not in f.parts and "web" not in f.parts, f
    rng = random.Random(seed)
    n_real = min(len(real), count)
    files = rng.sample(real, n_real) + rng.sample(synth, min(len(synth), count - n_real))
    print(f"Calibration: {n_real} real train photos + {len(files) - n_real} synthetic val images at {size}px")
    return [preprocess(cv2.imread(str(f)), size)[0] for f in files], files


def quantize(fp32_path: Path, scheme: str, calib, out_path: Path, calib_cache: dict):
    """Post-training quantization of the FP32 end-to-end .tflite with ai-edge-quantizer; float I/O kept."""
    from ai_edge_quantizer import qtyping, quantizer, recipe

    ops = qtyping.TFLOperationName
    no_q = recipe.AlgorithmName.NO_QUANTIZE
    qt = quantizer.Quantizer(str(fp32_path))
    if scheme == "dynamic":
        qt.load_quantization_recipe(recipe.dynamic_wi8_afp32())
    elif scheme == "weight_only":
        qt.load_quantization_recipe(recipe.weight_only_wi8_afp32())
    else:
        qt.load_quantization_recipe(recipe.static_wi8_ai16() if scheme == "w8a16" else recipe.static_wi8_ai8())
        # Later matching entries win: keep these op groups float.
        if scheme == "static_e2e":
            qt.update_quantization_recipe(regex=E2E_POSTPROCESS_REGEX, operation_name=ops.ALL_SUPPORTED, algorithm_key=no_q)
        elif scheme == "static_head":
            qt.update_quantization_recipe(regex=DETECT_HEAD_REGEX, operation_name=ops.ALL_SUPPORTED, algorithm_key=no_q)
        for op in (ops.INPUT, ops.OUTPUT):  # float32 graph input/output (the app's contract)
            qt.update_quantization_recipe(regex=".*", operation_name=op, algorithm_key=no_q)
    result = None
    if qt.need_calibration:
        if "full" not in calib_cache:  # min/max of every float tensor: compute once, reuse for all static schemes
            full = quantizer.Quantizer(str(fp32_path))
            full.load_quantization_recipe(recipe.static_wi8_ai8())
            from ai_edge_litert.interpreter import Interpreter

            name = Interpreter(model_path=str(fp32_path)).get_signature_list()["serving_default"]["inputs"][0]
            print(f"  calibrating on {len(calib)} images (input '{name}')...")
            calib_cache["full"] = full.calibrate({"serving_default": [{name: x} for x in calib]})
        result = calib_cache["full"]
    qt.quantize(calibration_result=result).export_model(str(out_path), overwrite=True)
    return out_path


def export_ultralytics_int8(src: Path, size: int, calib_files, work: Path) -> Path:
    """Ultralytics' own quantize=8 LiteRT export (raw output, no end2end head). Evaluation only."""
    from ultralytics import YOLO

    cal = work / f"calib_{size}"
    (cal / "images").mkdir(parents=True, exist_ok=True)
    (cal / "labels").mkdir(exist_ok=True)
    for f in calib_files:
        dst = cal / "images" / f.name
        if not dst.exists():
            dst.symlink_to(f.resolve())
        lbl = f.parent.parent / "labels" / f.with_suffix(".txt").name
        if lbl.exists() and not (cal / "labels" / lbl.name).exists():
            (cal / "labels" / lbl.name).symlink_to(lbl.resolve())
    cards = ML / args.cards
    classes = yaml.safe_load(cards.read_text())["classes"]
    data = cal / "calib.yaml"
    data.write_text(yaml.safe_dump({"path": str(cal), "train": "images", "val": "images",
                                    "names": dict(enumerate(classes))}, sort_keys=False))
    sub = work / "ultralytics_int8"
    sub.mkdir(exist_ok=True)
    pt = sub / src.name
    shutil.copy2(src, pt)
    out = Path(YOLO(str(pt)).export(format="litert", imgsz=size, device="cpu", batch=1, quantize=8,
                                    data=str(data), nms=False))
    return out


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
    ap.add_argument("--int8", choices=(*INT8_SCHEMES, "all"),
                    help="also write an INT8 variant (see the scheme list above) and install that one")
    ap.add_argument("--calib-count", type=int, default=200, help="calibration images for the static --int8 schemes")
    ap.add_argument("--verify-dir", default=str(ML / "data/real_photos/train"))
    ap.add_argument("--verify-count", type=int, default=3)
    ap.add_argument("--conf", type=float, default=0.25)
    ap.add_argument("--no-install", action="store_true", help="don't copy into the app assets")
    ap.add_argument("--cards", default="config/cards.yaml", help="class list the checkpoint must match (relative to ml/)")
    ap.add_argument("--asset-dir", default="",
                    help="assets subfolder of a game's own model (the app's DetectorModel), e.g. vengeance; "
                         "default: the assets root (Flip 7)")
    args = ap.parse_args()

    if args.int8 in ("all", "ultralytics"):
        args.no_install = True  # comparison runs / raw-output model: never install

    os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")
    import torch
    from ultralytics import YOLO

    torch.set_num_threads(max(1, (os.cpu_count() or 4) // 2))  # leave CPU for the running training
    weights = Path(args.weights).resolve()
    cards = ML / args.cards
    classes = yaml.safe_load(cards.read_text())["classes"]
    model = YOLO(str(weights))
    names = [model.names[i] for i in range(len(model.names))]
    if names != classes:
        sys.exit(f"Checkpoint class names {names} differ from {cards.name} {classes}")
    print(f"torch {torch.__version__}; checkpoint {weights} (epoch {model.ckpt.get('epoch')})")

    # Export from a copy so the .tflite lands in a scratch dir, never next to the (possibly live) checkpoint.
    work = ML / "out/export" / (f"{weights.parent.parent.name}_{weights.stem}"
                                + (f"_{args.imgsz}" if args.imgsz != 640 else ""))
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

    install, precision = tflite, "fp32"
    if args.int8:
        schemes = INT8_SCHEMES if args.int8 == "all" else (args.int8,)
        calib, calib_files, cache = None, None, {}
        for scheme in schemes:
            if scheme not in ("dynamic", "weight_only") and calib is None:
                calib, calib_files = calibration_images(args.imgsz, args.calib_count)
            print(f"\nINT8 scheme '{scheme}':")
            if scheme == "ultralytics":
                q = export_ultralytics_int8(src, args.imgsz, calib_files, work)
                print(f"  {q} ({q.stat().st_size / 1e6:.2f} MB): raw output, needs NMS; not verified against the "
                      "end-to-end PyTorch head (evaluate with scripts/eval_tflite.py)")
                describe(q)
                continue
            q = quantize(tflite, scheme, calib, tflite.with_name(f"{tflite.stem}_{scheme}.tflite"), cache)
            print(f"  {q} ({q.stat().st_size / 1e6:.2f} MB)")
            describe(q)
            # Informational: INT8 boxes/scores legitimately drift from FP32; accuracy is measured by eval_tflite.py.
            print(f"  PyTorch vs {scheme} on {len(photos)} photos:")
            q_ok = verify(model, q, photos, args.imgsz, args.conf)
            print(f"  {scheme} verification (IoU>=0.9, informational):", "PASS" if q_ok else "differs")
            install, precision = q, f"int8-{scheme}"

    if not args.no_install:
        assets = ASSETS / args.asset_dir
        assets.mkdir(parents=True, exist_ok=True)
        shutil.copy2(install, assets / "model.tflite")
        (assets / "labels.txt").write_text("\n".join(classes) + "\n")
        epoch = model.ckpt.get("epoch")
        version = (f"{weights.parent.parent.name}/{weights.stem}"
                   + (f" ep{epoch}" if epoch is not None and epoch >= 0 else "")
                   + f" · {precision} · {args.imgsz}px · {dt.date.today().isoformat()}")
        (assets / "model_version.txt").write_text(version + "\n")
        print(f"Installed into {assets}: model.tflite, labels.txt, model_version.txt ({version})")


if __name__ == "__main__":
    main()
