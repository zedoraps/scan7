"""Text-independence test (BRIEF milestone 4): the same synthetic test scenes with different banner text.

Generates one fixed set of scenes (fixed seed, mostly "row" layout) several times; only the text on
the cards differs between the variants:

    de      number-card banners as photographed (German: NULL, EINS, ... ZWÖLF)
    en      German word erased (as gen_synth's fake-word path does) and the English word drawn
            in a similar outlined ochre geometric font (ZERO ... TWELVE, never used in training)
    blank   German word erased, banner left cream
    en_mod  English banners + English small lines on the modifier cards
            ("THE SUM OF YOUR NUMBER CARDS +4" instead of "DIE SUMME DEINER ZAHLENKARTEN +4")

All variants consume the random stream identically, so card layout, pose, lighting, occluders and
labels are the same image by image. Output: ml/out/langtest/<variant>/{images,labels}/val + dataset.yaml.

    uv run python scripts/lang_test.py gen [--count 300] [--seed 4242] [--workers 6]
    uv run python scripts/lang_test.py eval            # Ultralytics val, CPU -> results.json
    uv run python scripts/lang_test.py examples        # examples_<variant>.jpg with predictions

gen_synth.py is not modified: its functions are reused and only patched inside this process.
"""

from __future__ import annotations

import argparse
import copy
import json
import multiprocessing as mp
import shutil
import sys
import time
from pathlib import Path

import cv2
import numpy as np
import yaml
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_synth as gs  # noqa: E402

ML = gs.ML
OUT = ML / "out" / "langtest"
VARIANTS = ["de", "en", "blank", "en_mod"]
EN_WORDS = ["ZERO", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN",
            "ELEVEN", "TWELVE"]
MOD_TEXT = {"plus2": "+2", "plus4": "+4", "plus6": "+6", "plus8": "+8", "plus10": "+10", "x2": "×2"}
BANNER_FONT = ("/System/Library/Fonts/Supplemental/Futura.ttc", 2)  # Futura Bold
MOD_FONT = ("/System/Library/Fonts/Avenir Next.ttc", 2)  # Avenir Next Demi Bold
MODELS = {
    "ft_v1": (ML / "runs" / "ft_v1" / "weights" / "best.pt", 640),
    "synth_v1": (ML / "out" / "snapshots" / "synth_v1_best.pt", 640),
    "ft_v3_ep11": (ML / "runs" / "ft_v3" / "weights" / "last.pt", 800),
}


# ---------------------------------------------------------------- text replacement on cutouts


def _pil_font(spec, size):
    return ImageFont.truetype(spec[0], size, index=spec[1])


def draw_spaced(roi, text, fspec, cap_h, fill, stroke, stroke_w, tracking, cx=None, cy=None):
    """Draw `text` (upper case) into BGR roi with cap height ~cap_h px, centred at (cx, cy)."""
    h, w = roi.shape[:2]
    cx = w / 2 if cx is None else cx
    cy = h / 2 if cy is None else cy
    size = max(6, int(cap_h * 1.4))
    for _ in range(30):  # fit the cap height of "H"
        f = _pil_font(fspec, size)
        hb = f.getbbox("H")
        if hb[3] - hb[1] <= cap_h:
            break
        size -= 1
    widths = [f.getlength(ch) for ch in text]
    gap = tracking * cap_h
    total = sum(widths) + gap * (len(text) - 1)
    if total > 0.96 * w:  # too long: shrink tracking first, then the font
        gap = max(0.0, (0.96 * w - sum(widths)) / max(1, len(text) - 1))
        total = sum(widths) + gap * (len(text) - 1)
        if total > 0.96 * w:
            s = 0.96 * w / total
            f = _pil_font(fspec, max(6, int(size * s)))
            widths = [f.getlength(ch) for ch in text]
            gap *= s
            total = sum(widths) + gap * (len(text) - 1)
            hb = f.getbbox("H")
    # supersample for clean anti-aliasing
    k = 4
    pil = Image.fromarray(cv2.cvtColor(roi, cv2.COLOR_BGR2RGB)).resize((w * k, h * k), Image.BICUBIC)
    fk = _pil_font(fspec, f.size * k)
    d = ImageDraw.Draw(pil)
    x = (cx - total / 2) * k
    y = (cy - (hb[1] + hb[3]) / 2) * k
    for ch, cw in zip(text, widths):
        d.text((x, y), ch, font=fk, fill=fill[::-1], stroke_width=int(round(stroke_w * k)),
               stroke_fill=stroke[::-1] if stroke is not None else None)
        x += (cw + gap) * k
    pil = pil.resize((w, h), Image.LANCZOS)
    roi[:] = cv2.cvtColor(np.asarray(pil), cv2.COLOR_RGB2BGR)


def erase_banner(rgb, inner):
    """Same erase as gen_synth.randomize_text ('fill' mode). Returns (roi_erased, roi_box, text_mask)."""
    h, w = rgb.shape[:2]
    x0, y0, x1, y1 = (int(round(v * s)) for v, s in zip(inner, (w, h, w, h)))
    roi = np.ascontiguousarray(rgb[y0:y1, x0:x1])
    lab = cv2.cvtColor(roi, cv2.COLOR_BGR2LAB).astype(np.float32)
    cream = np.median(lab.reshape(-1, 3), axis=0)
    dist = np.linalg.norm(lab - cream, axis=2)
    m = (dist > 18).astype(np.uint8) * 255
    md = cv2.dilate(m, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7)))
    erased = cv2.inpaint(roi, md, 5, cv2.INPAINT_TELEA)
    # umlaut dots (Ü, Ö) reach above the inner box into the banner's top frame lines. The frame
    # lines are horizontal, so rebuild the strip above the word from the per-row median of the
    # columns beside the word wherever it differs from that profile.
    cols = np.flatnonzero(m[int(m.shape[0] * 0.2):int(m.shape[0] * 0.8), int(m.shape[1] * 0.04):int(m.shape[1] * 0.96)]
                          .any(axis=0)) + int(m.shape[1] * 0.04)
    if len(cols):
        ya = max(0, y0 - int(round(0.03 * h)))
        lx0, lx1 = x0 + max(0, cols[0] - 6), x0 + min(x1 - x0, cols[-1] + 7)
        strip = rgb[ya:y0 + 3, x0:x1].astype(np.float32)
        side = np.r_[0:max(1, lx0 - x0 - 4), min(x1 - x0 - 1, lx1 - x0 + 4):x1 - x0]
        ref = np.median(strip[:, side], axis=1)  # (rows, 3)
        seg = strip[:, lx0 - x0:lx1 - x0]
        dm = (np.abs(seg - ref[:, None]).max(axis=2) > 25).astype(np.uint8)
        dm = cv2.dilate(dm, np.ones((3, 3), np.uint8)) > 0
        seg[dm] = np.broadcast_to(ref[:, None], seg.shape)[dm]
        rgb[ya:y0, lx0:lx1] = np.clip(seg[: y0 - ya], 0, 255).astype(np.uint8)
    return erased, (x0, y0, x1, y1), m > 0, roi


def banner_variant(rgba, cls_idx, mode, inner):
    out = rgba.copy()
    rgb = np.ascontiguousarray(out[..., :3])
    erased, (x0, y0, x1, y1), m, orig = erase_banner(rgb, inner)
    if mode == "en":
        # colours and size of the printed German word
        lab = cv2.cvtColor(orig, cv2.COLOR_BGR2LAB)
        # ignore the frame lines at the roi border: keep the central rows/cols only
        core = np.zeros_like(m)
        hh, ww = m.shape
        core[int(hh * 0.12):int(hh * 0.88), int(ww * 0.04):int(ww * 0.96)] = True
        mt = m & core
        ys = np.flatnonzero(mt.any(axis=1))
        cap_h = (ys[-1] - ys[0] + 1) if len(ys) else hh * 0.6
        L = lab[..., 0][mt].astype(np.float32)
        px = orig[mt].astype(np.float32)
        dark = px[L < np.percentile(L, 35)]
        light = px[L > np.percentile(L, 65)]
        stroke = tuple(int(v) for v in np.median(dark, axis=0))
        fill = tuple(int(v) for v in np.median(light, axis=0))
        cy = (ys[0] + ys[-1]) / 2 if len(ys) else hh / 2
        draw_spaced(erased, EN_WORDS[cls_idx], BANNER_FONT, cap_h * 0.92, fill, stroke,
                    stroke_w=max(1.0, cap_h * 0.07), tracking=0.32, cy=cy)
        erased = cv2.GaussianBlur(erased, (0, 0), 0.6)
    out[..., :3] = rgb
    out[y0:y1, x0:x1, :3] = erased
    return out


def modifier_variant(rgba, cls):
    """Replace both small German lines on a +N / x2 card with English text."""
    out = rgba.copy()
    rgb = np.ascontiguousarray(out[..., :3])
    h, w = rgb.shape[:2]
    lab = cv2.cvtColor(rgb, cv2.COLOR_BGR2LAB).astype(np.float32)
    xa, xb = int(0.10 * w), int(0.90 * w)
    for ya, yb in ((int(0.225 * h), int(0.275 * h)), (int(0.715 * h), int(0.765 * h))):
        sub = lab[ya:yb, xa:xb]
        bg = np.median(sub.reshape(-1, 3), axis=0)
        # purple ink: much lower b (less yellow) than the orange background
        m = (bg[2] - sub[..., 2]) > 22
        m = cv2.morphologyEx(m.astype(np.uint8), cv2.MORPH_OPEN, np.ones((2, 2), np.uint8)) > 0
        ys, xs = np.flatnonzero(m.any(axis=1)), np.flatnonzero(m.any(axis=0))
        if len(ys) < 3:
            raise RuntimeError(f"{cls}: modifier text line not found in rows {ya}-{yb}")
        ty0, ty1, tx0, tx1 = ys[0], ys[-1] + 1, xs[0], xs[-1] + 1
        roi = np.ascontiguousarray(rgb[ya:yb, xa:xb])
        ink = tuple(int(v) for v in np.median(roi[m].reshape(-1, 3), axis=0))
        # erase: replace the padded text box by a per-column blend of the rows above and below
        # (the orange background is a smooth gradient), plus grain like the surrounding print
        p0, p1 = max(2, ty0 - 4), min(roi.shape[0] - 2, ty1 + 4)
        q0, q1 = int(0.115 * w) - xa, int(0.885 * w) - xa  # whole line width (between the frame posts)
        above = roi[p0 - 2:p0, q0:q1].astype(np.float32).mean(axis=0)
        below = roi[p1:p1 + 2, q0:q1].astype(np.float32).mean(axis=0)
        above = cv2.GaussianBlur(above[None], (0, 0), 3)[0]
        below = cv2.GaussianBlur(below[None], (0, 0), 3)[0]
        t = np.linspace(0, 1, p1 - p0, dtype=np.float32)[:, None, None]
        fillr = above[None] * (1 - t) + below[None] * t
        grain = roi[:p0].astype(np.float32).std(axis=(0, 1)).mean() * 0.5
        fillr += np.random.default_rng(len(cls)).normal(0, grain, fillr.shape[:2])[..., None]
        roi[p0:p1, q0:q1] = np.clip(fillr, 0, 255).astype(np.uint8)
        text = f"THE SUM OF YOUR NUMBER CARDS {MOD_TEXT[cls]}"
        draw_spaced(roi, text, MOD_FONT, (ty1 - ty0) * 0.95, ink, None, 0, tracking=0.12,
                    cx=(tx0 + tx1) / 2, cy=(ty0 + ty1) / 2)
        roi = cv2.GaussianBlur(roi, (0, 0), 0.5)
        rgb[ya:yb, xa:xb] = roi
    out[..., :3] = rgb
    return out


# ---------------------------------------------------------------- generation (patched gen_synth worker)


def init_worker(cfg, cards_cfg, bg_paths, variant):
    gs.init_worker(cfg, cards_cfg, bg_paths)
    G = gs.G
    classes = G["classes"]
    inner = cfg["text"]["inner"]
    lut = {}  # id(original cutout) -> replaced cutout
    if variant != "de":
        mode = "blank" if variant == "blank" else "en"
        for c in cards_cfg["number_classes"]:
            for im in G["cutouts"][c]:
                lut[id(im)] = banner_variant(im, classes.index(c), mode, inner)
    if variant == "en_mod":
        for c in MOD_TEXT:
            G["cutouts"][c] = [modifier_variant(im, c) for im in G["cutouts"][c]]
    G["lut"] = lut
    # every number card passes through randomize_text (cfg text.p = 1, so the rng stream is
    # identical for all variants); the replacement is deterministic and uses no randomness.
    gs.randomize_text = lambda rng, rgba, cfg_: G["lut"].get(id(rgba), rgba)


def test_cfg(base: dict) -> dict:
    cfg = copy.deepcopy(base)
    cfg["layout_weights"] = {"row": 0.8, "scattered": 0.1, "large": 0.1, "pile": 0.0}
    cfg["text"]["p"] = 1.0  # see init_worker
    cfg["occluders"]["p_image"] = 0.2  # fewer hidden banners
    return cfg


def cmd_gen(args):
    cfg = test_cfg(gs.load_yaml(gs.SYNTH_CONFIG))
    cards_cfg = gs.load_yaml(gs.CARDS_CONFIG)
    classes = cards_cfg["classes"]
    bgs = gs.prepare_backgrounds(cfg, args.workers)
    OUT.mkdir(parents=True, exist_ok=True)
    ctx = mp.get_context("spawn")
    for v in args.variants:
        out = OUT / v
        if out.exists():
            shutil.rmtree(out)
        (out / "images" / "val").mkdir(parents=True)
        (out / "labels" / "val").mkdir(parents=True)
        tasks = [(i, args.seed, "val", out) for i in range(args.count)]
        t0 = time.time()
        n_lab = 0
        with ctx.Pool(args.workers, initializer=init_worker, initargs=(cfg, cards_cfg, bgs, v)) as pool:
            for _, _, cls_ids, _, _ in pool.imap_unordered(gs.generate, tasks, chunksize=2):
                n_lab += len(cls_ids)
        ds = {"path": str(out), "train": "images/val", "val": "images/val", "names": dict(enumerate(classes))}
        with open(out / "dataset.yaml", "w") as f:
            f.write(f"# text-independence test variant '{v}', generated by scripts/lang_test.py (seed {args.seed})\n")
            yaml.safe_dump(ds, f, sort_keys=False)
        print(f"{v}: {args.count} images, {n_lab} labels in {time.time() - t0:.0f}s -> {out}", flush=True)
    # sanity: labels identical across variants
    ref = sorted((OUT / args.variants[0] / "labels" / "val").glob("*.txt"))
    for v in args.variants[1:]:
        same = all((OUT / v / "labels" / "val" / p.name).read_text() == p.read_text() for p in ref)
        print(f"labels {args.variants[0]} == {v}: {same}")


# ---------------------------------------------------------------- evaluation


def cmd_eval(args):
    from ultralytics import YOLO

    cards_cfg = gs.load_yaml(gs.CARDS_CONFIG)
    classes = cards_cfg["classes"]
    res_path = OUT / "results.json"
    results = json.loads(res_path.read_text()) if res_path.exists() else {}
    for mname in args.models:
        wpath, imgsz = MODELS[mname]
        for v in args.variants:
            print(f"== {mname} on {v}", flush=True)
            model = YOLO(str(wpath))
            m = model.val(data=str(OUT / v / "dataset.yaml"), split="val", imgsz=imgsz, conf=0.25, iou=0.7,
                          device="cpu", batch=8, workers=0, plots=True, verbose=False,
                          project=str(OUT / "val"), name=f"{mname}_{v}", exist_ok=True)
            cm = m.confusion_matrix.matrix  # [pred, true], last = background
            nc = len(classes)
            gt = cm.sum(axis=0)
            pred = cm.sum(axis=1)
            diag = np.diag(cm)
            box = m.box
            ap_idx = list(box.ap_class_index)
            results.setdefault(mname, {})[v] = {
                "map50": float(box.map50), "map": float(box.map), "p": float(box.mp), "r": float(box.mr),
                "cm": cm.tolist(),
                "cm_recall": {classes[i]: float(diag[i] / gt[i]) if gt[i] else None for i in range(nc)},
                "cm_precision": {classes[i]: float(diag[i] / pred[i]) if pred[i] else None for i in range(nc)},
                "ap50": {classes[c]: float(box.ap50[k]) for k, c in enumerate(ap_idx)},
                "n_gt": {classes[i]: int(gt[i]) for i in range(nc)},
            }
            res_path.write_text(json.dumps(results))
            print(f"   mAP50 {box.map50:.3f}  P {box.mp:.3f}  R {box.mr:.3f}", flush=True)


# ---------------------------------------------------------------- examples


def cmd_examples(args):
    from ultralytics import YOLO

    mname = args.example_model
    wpath, imgsz = MODELS[mname]
    model = YOLO(str(wpath))
    names = model.names
    for v in VARIANTS:
        tiles = []
        for stem in args.example_images:
            p = OUT / v / "images" / "val" / f"{stem}.jpg"
            r = model.predict(str(p), imgsz=imgsz, conf=0.25, device="cpu", verbose=False)[0]
            img = cv2.imread(str(p))
            h, w = img.shape[:2]
            s = 1100 / max(h, w)
            img = cv2.resize(img, None, fx=s, fy=s, interpolation=cv2.INTER_AREA)
            for b, c, cf in zip(r.boxes.xyxy.numpy() * s, r.boxes.cls.numpy().astype(int), r.boxes.conf.numpy()):
                x0, y0, x1, y1 = b.astype(int)
                col = (60, 200, 60)
                cv2.rectangle(img, (x0, y0), (x1, y1), col, 2)
                t = f"{names[c]} {cf:.2f}"
                (tw, th), _ = cv2.getTextSize(t, cv2.FONT_HERSHEY_SIMPLEX, 0.55, 1)
                cv2.rectangle(img, (x0, max(0, y0 - th - 6)), (x0 + tw + 4, y0), col, -1)
                cv2.putText(img, t, (x0 + 2, y0 - 4), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 0, 0), 1, cv2.LINE_AA)
            cv2.putText(img, f"{v} / {stem} / {mname}", (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 0, 0), 4, cv2.LINE_AA)
            cv2.putText(img, f"{v} / {stem} / {mname}", (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (255, 255, 255), 2, cv2.LINE_AA)
            tiles.append(img)
        hmax = max(t.shape[0] for t in tiles)
        tiles = [cv2.copyMakeBorder(t, 0, hmax - t.shape[0], 0, 8, cv2.BORDER_CONSTANT, value=(40, 40, 40)) for t in tiles]
        cv2.imwrite(str(OUT / f"examples_{v}.jpg"), np.hstack(tiles), [cv2.IMWRITE_JPEG_QUALITY, 90])
        print(OUT / f"examples_{v}.jpg")
    # banner close-ups straight from the replaced cutouts (no scene), for checking the rendering
    cards_cfg = gs.load_yaml(gs.CARDS_CONFIG)
    cfg = gs.load_yaml(gs.SYNTH_CONFIG)
    cut = gs.load_cutouts(cards_cfg["classes"])
    rows = []
    for c in cards_cfg["number_classes"]:
        im = cut[c][0]
        y0, y1 = int(0.74 * im.shape[0]), int(0.88 * im.shape[0])
        row = [im[y0:y1, :, :3]]
        for mode in ("en", "blank"):
            row.append(banner_variant(im, cards_cfg["classes"].index(c), mode, cfg["text"]["inner"])[y0:y1, :, :3])
        rows.append(np.hstack(row))
    mods = []
    for c in MOD_TEXT:
        im = cut[c][0]
        new = modifier_variant(im, c)
        mods.append(np.hstack([im[180:240, :, :3], new[180:240, :, :3], new[585:645, :, :3]]))
    sheet = np.vstack(rows + mods)
    cv2.imwrite(str(OUT / "examples_closeups.jpg"), sheet, [cv2.IMWRITE_JPEG_QUALITY, 90])
    print(OUT / "examples_closeups.jpg")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cmd", choices=["gen", "eval", "examples"])
    ap.add_argument("--count", type=int, default=300)
    ap.add_argument("--seed", type=int, default=4242)
    ap.add_argument("--workers", type=int, default=6)
    ap.add_argument("--variants", nargs="+", default=VARIANTS)
    ap.add_argument("--models", nargs="+", default=list(MODELS))
    ap.add_argument("--example-model", default="ft_v1")
    ap.add_argument("--example-images", nargs="+", default=["000003", "000010"])
    args = ap.parse_args()
    {"gen": cmd_gen, "eval": cmd_eval, "examples": cmd_examples}[args.cmd](args)


if __name__ == "__main__":
    main()
