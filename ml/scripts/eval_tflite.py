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
"""Evaluate exported .tflite detectors on a labeled YOLO folder, the way the app runs them.

Same env as export.py (PEP 723 header): `uv run scripts/eval_tflite.py ...` (NOT `uv run python`).

    cd ml
    uv run scripts/eval_tflite.py out/export/ft_v1_best/best.tflite out/export/ft_v1_best/best_static_e2e.tflite \\
        --data out/real/test --json out/export/eval_test.json

Per model: app preprocessing (centered letterbox, pad 114, RGB 0-1, NCHW float), end-to-end output [1, N, 6]
parsed like the app's parseEndToEnd + suppressDuplicates (class-agnostic, IoU > 0.6). A raw output
[1, 4+nc, anchors] (Ultralytics INT8 export without the end2end head) gets class-aware NMS (IoU 0.7, the
Ultralytics default) first - that is the path the app would need for such a model.
Reports mAP50 (all scores >= 0.001, COCO 101-point interpolation, mean over classes present in the labels),
P / R and top class confusions at the app's accept score (0.35), agreement with the first model, file size,
output shape and the median CPU time of --runs invocations (XNNPACK, --threads threads).
"""
import argparse
import json
import statistics
import sys
import time
from collections import Counter
from pathlib import Path

import numpy as np
import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
from export import ML, iou, preprocess  # noqa: E402

ACCEPT_SCORE = 0.35  # app: DetectionTracker.kt ACCEPT_SCORE (= CardDetector DEFAULT_CONFIDENCE)
DUPLICATE_IOU = 0.6  # app: DetectionMath.kt DUPLICATE_IOU
AP_CONF = 0.001
CLASSES = yaml.safe_load((ML / "config/cards.yaml").read_text())["classes"]


def suppress_duplicates(dets):
    kept = []
    for d in sorted(dets, key=lambda d: -d[1]):
        if all(iou(k[2:], d[2:]) <= DUPLICATE_IOU for k in kept):
            kept.append(d)
    return kept


def nms_raw(out, size, conf, iou_thr=0.7, max_det=300):
    """Raw [4+nc, A] head output -> [(cls, score, x1, y1, x2, y2)] in model-input pixels, class-aware NMS."""
    boxes, scores = out[:4].T, out[4:].T
    if boxes.max() <= 2.0:  # Ultralytics' LiteRT INT8 export normalizes xywh by the input size
        boxes = boxes * size
    cls = scores.argmax(1)
    sc = scores[np.arange(len(cls)), cls]
    keep = np.where(sc >= conf)[0]
    keep = keep[np.argsort(-sc[keep])]
    xyxy = np.stack([boxes[:, 0] - boxes[:, 2] / 2, boxes[:, 1] - boxes[:, 3] / 2,
                     boxes[:, 0] + boxes[:, 2] / 2, boxes[:, 1] + boxes[:, 3] / 2], 1)
    kept = []
    for i in keep:
        if all(cls[j] != cls[i] or iou(xyxy[j], xyxy[i]) <= iou_thr for j in kept):
            kept.append(i)
            if len(kept) >= max_det:
                break
    return [(int(cls[i]), float(sc[i]), *map(float, xyxy[i])) for i in kept]


class Model:
    def __init__(self, path: Path, threads: int):
        from ai_edge_litert.interpreter import Interpreter

        self.path = path
        self.interp = Interpreter(model_path=str(path), num_threads=threads)
        self.interp.allocate_tensors()
        self.inp = self.interp.get_input_details()[0]
        self.out = self.interp.get_output_details()[0]
        self.nchw = self.inp["shape"][1] == 3
        self.size = int(self.inp["shape"][2])
        self.out_shape = [int(v) for v in self.out["shape"]]
        self.end2end = self.out_shape[-1] == 6

    def run(self, x):
        self.interp.set_tensor(self.inp["index"], x)
        self.interp.invoke()
        return self.interp.get_tensor(self.out["index"])[0]

    def predict(self, img_bgr, conf=AP_CONF):
        x, scale, px, py = preprocess(img_bgr, self.size, self.nchw)
        out = self.run(x)
        if self.end2end:
            raw = [(int(round(c)), float(s), x1, y1, x2, y2) for x1, y1, x2, y2, s, c in out if s >= conf]
        else:
            raw = nms_raw(out, self.size, conf)
        dets = [(c, s, (x1 - px) / scale, (y1 - py) / scale, (x2 - px) / scale, (y2 - py) / scale)
                for c, s, x1, y1, x2, y2 in raw if 0 <= c < len(CLASSES)]
        return suppress_duplicates(dets)

    def time_ms(self, img_bgr, runs):
        x, *_ = preprocess(img_bgr, self.size, self.nchw)
        for _ in range(3):
            self.run(x)
        t = []
        for _ in range(runs):
            t0 = time.perf_counter()
            self.run(x)
            t.append((time.perf_counter() - t0) * 1e3)
        return statistics.median(t)


def load_gt(label_file: Path, w, h):
    gt = []
    if label_file.exists():
        for line in label_file.read_text().split("\n"):
            if line.strip():
                c, cx, cy, bw, bh = line.split()[:5]
                cx, cy, bw, bh = float(cx) * w, float(cy) * h, float(bw) * w, float(bh) * h
                gt.append((int(c), cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2))
    return gt


def match(dets, gt, thr=0.5, class_aware=True):
    """Greedy by score. Returns (list of (det, gt_index or None)), matched gt indices."""
    used, res = set(), []
    for d in sorted(dets, key=lambda d: -d[1]):
        best, bi = thr, None
        for i, g in enumerate(gt):
            if i in used or (class_aware and g[0] != d[0]):
                continue
            v = iou(d[2:], g[1:])
            if v >= best:
                best, bi = v, i
        if bi is not None:
            used.add(bi)
        res.append((d, bi))
    return res, used


def ap101(tp, conf, n_gt):
    if n_gt == 0:
        return None
    if not tp:
        return 0.0
    order = np.argsort(-np.array(conf))
    tp = np.array(tp, float)[order]
    ctp, cfp = np.cumsum(tp), np.cumsum(1 - tp)
    recall = ctp / n_gt
    precision = ctp / (ctp + cfp)
    mrec = np.concatenate([[0.0], recall, [1.0]])
    mpre = np.concatenate([[1.0], precision, [0.0]])
    mpre = np.flip(np.maximum.accumulate(np.flip(mpre)))
    x = np.linspace(0, 1, 101)
    return float(np.trapezoid(np.interp(x, mrec, mpre), x))


def evaluate(model: Model, images, labels_dir: Path, ref_preds=None):
    import cv2

    per_cls_tp, per_cls_conf, n_gt = [[] for _ in CLASSES], [[] for _ in CLASSES], Counter()
    tp = fp = fn = 0
    confusions, missed, extra = Counter(), Counter(), Counter()
    preds, agree, ref_total = {}, 0, 0
    for f in images:
        img = cv2.imread(str(f))
        h, w = img.shape[:2]
        gt = load_gt(labels_dir / f.with_suffix(".txt").name, w, h)
        dets = model.predict(img)
        preds[f.name] = [d for d in dets if d[1] >= ACCEPT_SCORE]
        for g in gt:
            n_gt[g[0]] += 1
        res, _ = match(dets, gt)
        for d, gi in res:
            per_cls_tp[d[0]].append(1 if gi is not None else 0)
            per_cls_conf[d[0]].append(d[1])
        acc = preds[f.name]
        res, used = match(acc, gt)
        tp += len(used)
        fp += sum(1 for _, gi in res if gi is None)
        fn += len(gt) - len(used)
        # Confusions: class-agnostic match of the accepted boxes.
        res_any, used_any = match(acc, gt, class_aware=False)
        for d, gi in res_any:
            if gi is None:
                extra[CLASSES[d[0]]] += 1
            elif gt[gi][0] != d[0]:
                confusions[(CLASSES[gt[gi][0]], CLASSES[d[0]])] += 1
        for i, g in enumerate(gt):
            if i not in used_any:
                missed[CLASSES[g[0]]] += 1
        if ref_preds is not None:
            ref = ref_preds[f.name]
            ref_total += len(ref)
            agree += len(match(acc, [(d[0], *d[2:]) for d in ref])[1])
    aps = {CLASSES[c]: ap101(per_cls_tp[c], per_cls_conf[c], n_gt[c]) for c in range(len(CLASSES)) if n_gt[c]}
    return {
        "mAP50": float(np.mean(list(aps.values()))) if aps else 0.0,
        "P": tp / (tp + fp) if tp + fp else 0.0,
        "R": tp / (tp + fn) if tp + fn else 0.0,
        "tp": tp, "fp": fp, "fn": fn,
        "agree_with_ref": (agree / ref_total) if ref_preds is not None and ref_total else None,
        "per_class_ap": aps,
        "confusions": [f"{a}->{b} x{n}" for (a, b), n in confusions.most_common()],
        "missed": dict(missed.most_common()),
        "extra": dict(extra.most_common()),
    }, preds


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("models", nargs="+", help=".tflite files; the first is the reference for 'agree'")
    ap.add_argument("--data", action="append", help="YOLO folder with images/ and labels/ (repeatable)")
    ap.add_argument("--threads", type=int, default=4)
    ap.add_argument("--runs", type=int, default=50)
    ap.add_argument("--json", help="write all results here")
    args = ap.parse_args()
    datasets = [Path(d) for d in (args.data or [str(ML / "out/real/test")])]

    results = []
    ref_preds = {}
    for k, path in enumerate(map(Path, args.models)):
        m = Model(path, args.threads)
        row = {"model": str(path), "size_mb": path.stat().st_size / 1e6, "output_shape": m.out_shape,
               "end2end": m.end2end, "input_size": m.size, "datasets": {}}
        for ds in datasets:
            images = sorted(p for p in (ds / "images").iterdir() if p.suffix.lower() in (".jpg", ".jpeg", ".png"))
            if not images:
                print(f"  {ds}: no images, skipped")
                continue
            r, preds = evaluate(m, images, ds / "labels", ref_preds.get(ds) if k else None)
            if k == 0:
                ref_preds[ds] = preds
            r["images"] = len(images)
            row["datasets"][str(ds)] = r
            if "cpu_ms" not in row:
                import cv2

                row["cpu_ms"] = m.time_ms(cv2.imread(str(images[0])), args.runs)
        results.append(row)
        for ds, r in row["datasets"].items():
            agree = "" if r["agree_with_ref"] is None else f" agree {r['agree_with_ref']:.3f}"
            print(f"{path.name:32s} {Path(ds).name:6s} {row['size_mb']:5.2f} MB {str(m.out_shape):16s} "
                  f"mAP50 {r['mAP50']:.3f} P {r['P']:.3f} R {r['R']:.3f} (tp {r['tp']} fp {r['fp']} fn {r['fn']})"
                  f"{agree} cpu {row['cpu_ms']:.1f} ms")
            print(f"    confusions: {', '.join(r['confusions'][:6]) or '-'}; missed {r['missed']}; extra {r['extra']}")
    if args.json:
        Path(args.json).write_text(json.dumps(results, indent=1))
        print(f"wrote {args.json}")


if __name__ == "__main__":
    main()
