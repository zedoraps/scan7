"""Stage 2: generate a synthetic YOLO dataset of Flip 7 cards on table backgrounds.

Each image is composed top-down (row / scattered / pile / large-card layouts from
config/synth.yaml), then rendered through a whole-scene phone-camera perspective (tilt 0-45°),
with per-card and whole-image photometric augmentation, banner-text randomization and
occluders. Labels are the axis-aligned bounds of each card's *visible* pixels in the final
image; cards hidden more than ~70% (occluded or outside the frame) get no label.

    uv run python scripts/gen_synth.py --count 200            # -> ml/out/synth
    uv run python scripts/gen_synth.py --count 5000 --seed 1 --workers 10

Output (Ultralytics layout): <out>/images/{train,val}/*.jpg, <out>/labels/{train,val}/*.txt,
<out>/dataset.yaml. The output dir is wiped first (only if it is empty or was made by this
script). Deterministic per --seed (independent of --workers).
"""

from __future__ import annotations

import argparse
import multiprocessing as mp
import shutil
import time
from collections import Counter
from pathlib import Path

import cv2
import numpy as np
import yaml
from PIL import Image, ImageDraw, ImageFont

ML = Path(__file__).resolve().parents[1]
CARDS_CONFIG = ML / "config" / "cards.yaml"
SYNTH_CONFIG = ML / "config" / "synth.yaml"
CUTOUTS = ML / "out" / "cutouts"
BACKGROUNDS = ML / "data" / "backgrounds"
CACHE = ML / "out" / "cache"
DEFAULT_OUT = ML / "out" / "synth"
MARKER = ".gen_synth"  # marks an output dir as ours (safe to wipe)

SKIN_TONES = [  # BGR
    (172, 196, 241), (150, 180, 230), (128, 160, 214), (110, 140, 196), (90, 118, 170),
    (70, 95, 140), (55, 72, 110), (160, 185, 225), (140, 170, 222),
]
PEN_COLORS = [(40, 30, 25), (120, 50, 20), (30, 30, 170), (230, 230, 230), (40, 120, 40), (20, 160, 220),
              (90, 90, 90), (160, 60, 120)]
TEXT_FILLS = [(70, 185, 225), (60, 170, 215), (90, 200, 235), (60, 150, 200), (75, 95, 120), (60, 70, 95),
              (55, 60, 75)]  # BGR: ochre/yellow like the print, sometimes brown/dark ink
TEXT_STROKES = [(70, 40, 45), (60, 45, 60), (45, 45, 60), (40, 50, 70)]


def load_yaml(p: Path) -> dict:
    with open(p) as f:
        return yaml.safe_load(f)


def class_of(stem: str, classes: list[str]) -> str:
    """'12_2' -> '12', 'second_chance' -> 'second_chance'."""
    if stem in classes:
        return stem
    base, _, suffix = stem.rpartition("_")
    if base in classes and suffix.isdigit():
        return base
    raise ValueError(f"{stem}: not a known class name (see config/cards.yaml)")


# ---------------------------------------------------------------- random helpers


def U(rng, r) -> float:
    return float(rng.uniform(r[0], r[1]))


def RI(rng, r) -> int:
    return int(rng.integers(r[0], r[1] + 1))


def pick(rng, weights: dict):
    keys = list(weights)
    p = np.array([weights[k] for k in keys], np.float64)
    return keys[int(rng.choice(len(keys), p=p / p.sum()))]


# ---------------------------------------------------------------- matrices


def T(x, y):
    return np.array([[1, 0, x], [0, 1, y], [0, 0, 1]], np.float64)


def S(sx, sy=None):
    return np.diag([sx, sx if sy is None else sy, 1.0])


def R(deg):
    a = np.deg2rad(deg)
    c, s = np.cos(a), np.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], np.float64)


def apply_h(h, pts):
    pts = np.asarray(pts, np.float64)
    p = np.c_[pts, np.ones(len(pts))] @ h.T
    return p[:, :2] / p[:, 2:3]


def soft_blur(m, sigma):
    """Gaussian blur of a smooth mask; large sigmas run on a downscaled copy (fast)."""
    sigma = float(sigma)
    if sigma <= 6:
        return cv2.GaussianBlur(m, (0, 0), max(0.3, sigma))
    f = 3.0 / sigma
    h, w = m.shape[:2]
    small = cv2.resize(m, (max(1, int(w * f)), max(1, int(h * f))), interpolation=cv2.INTER_AREA)
    small = cv2.GaussianBlur(small, (0, 0), 3.0)
    return cv2.resize(small, (w, h), interpolation=cv2.INTER_LINEAR)


def poly_area(q):
    x, y = q[:, 0], q[:, 1]
    return 0.5 * abs(np.dot(x, np.roll(y, -1)) - np.dot(y, np.roll(x, -1)))


def camera_homography(rng, cfg, w, h):
    """Scene (top-down, w x h px) -> image of a phone orbiting the scene centre at a tilt."""
    c = cfg["camera"]
    f = 0.5 * max(w, h) / np.tan(np.deg2rad(U(rng, c["fov_long"])) / 2)
    lo, hi = c["tilt"]
    tilt = np.deg2rad(lo + (hi - lo) * rng.random() ** c.get("tilt_power", 1.0))
    phi = rng.uniform(0, 2 * np.pi)
    q, _ = cv2.Rodrigues(np.array([np.cos(phi), np.sin(phi), 0.0]) * tilt)
    qt = q.T
    # camera at distance f from the plane centre (unit scale at the centre), looking at it
    hcam = np.diag([f, f, 1.0]) @ np.c_[qt[:, 0], qt[:, 1], [0, 0, f]]
    hcam /= hcam[2, 2]
    post = T(w / 2 + U(rng, [-1, 1]) * c["shift"] * w, h / 2 + U(rng, [-1, 1]) * c["shift"] * h)
    post = post @ R(U(rng, [-c["roll"], c["roll"]])) @ S(U(rng, c["scale"]))
    return post @ hcam @ T(-w / 2, -h / 2)


# ---------------------------------------------------------------- assets


def bg_cache_dir(cfg) -> Path:
    return CACHE / f"backgrounds_{int(cfg['background']['cache_long_side'])}"


def _cache_one(args):
    src, dst, n = args
    img = cv2.imread(str(src), cv2.IMREAD_COLOR)
    if img is None:
        raise RuntimeError(f"cannot read {src}")
    s = n / max(img.shape[:2])
    if s < 1:
        img = cv2.resize(img, None, fx=s, fy=s, interpolation=cv2.INTER_AREA)
    cv2.imwrite(str(dst), img, [cv2.IMWRITE_JPEG_QUALITY, 95])
    return dst


def prepare_backgrounds(cfg, workers) -> list[Path]:
    """Pre-resize background photos once (cached by name + mtime)."""
    n = int(cfg["background"]["cache_long_side"])
    d = bg_cache_dir(cfg)
    d.mkdir(parents=True, exist_ok=True)
    jobs, out = [], []
    for f in sorted(BACKGROUNDS.glob("*.jpg")):
        dst = d / f.name
        if not dst.exists() or dst.stat().st_mtime < f.stat().st_mtime:
            jobs.append((f, dst, n))
        out.append(dst)
    if jobs:
        print(f"caching {len(jobs)} backgrounds at {n}px ...")
        with mp.Pool(workers) as pool:
            pool.map(_cache_one, jobs)
    if not out:
        raise RuntimeError(f"no backgrounds in {BACKGROUNDS}")
    return out


def load_cutouts(classes) -> dict[str, list[np.ndarray]]:
    cut: dict[str, list[np.ndarray]] = {c: [] for c in classes}
    for f in sorted(CUTOUTS.glob("*.png")):
        img = cv2.imread(str(f), cv2.IMREAD_UNCHANGED)
        if img is None or img.ndim != 3 or img.shape[2] != 4:
            continue
        cut[class_of(f.stem, classes)].append(img)
    missing = [c for c, v in cut.items() if not v]
    if missing:
        raise RuntimeError(f"missing cutouts for {missing} in {CUTOUTS} (run make_cutouts.py)")
    return cut


# ---------------------------------------------------------------- worker state

G: dict = {}


def init_worker(cfg, cards_cfg, bg_paths):
    cv2.setNumThreads(1)
    G["cfg"] = cfg
    G["classes"] = cards_cfg["classes"]
    G["number"] = set(cards_cfg["number_classes"])
    G["cutouts"] = load_cutouts(cards_cfg["classes"])
    G["bgs"] = bg_paths
    G["fonts"] = [p for p in cfg["text"]["fonts"] if Path(p).exists()]
    G["font_cache"] = {}


def font(path, size):
    key = (path, size)
    if key not in G["font_cache"]:
        G["font_cache"][key] = ImageFont.truetype(path, size)
    return G["font_cache"][key]


# ---------------------------------------------------------------- text randomization


def fake_word(rng, cfg) -> str:
    forbidden = set(cfg["text"]["forbidden"])
    while True:
        n = RI(rng, cfg["text"]["word_len"])
        # mix vowels in so the words look word-like
        vowels, cons = "AEIOU", "BCDFGHJKLMNPRSTVWXZ"
        w = "".join(rng.choice(list(vowels if (i % 2 == 1) ^ (rng.random() < 0.25) else cons)) for i in range(n))
        if w not in forbidden:
            return w


def render_word(rng, roi, word):
    """Draw `word` centred into the BGR roi (in place)."""
    h, w = roi.shape[:2]
    fill = tuple(int(np.clip(c + rng.normal(0, 12), 0, 255)) for c in TEXT_FILLS[rng.integers(len(TEXT_FILLS))])
    stroke = TEXT_STROKES[rng.integers(len(TEXT_STROKES))]
    target_h = int(h * rng.uniform(0.5, 0.75))
    spacing = rng.uniform(0.05, 0.3)
    if G["fonts"]:
        path = G["fonts"][rng.integers(len(G["fonts"]))]
        size = max(8, int(target_h * 1.3))
        for _ in range(12):
            f = font(path, size)
            widths = [f.getbbox(ch)[2] - f.getbbox(ch)[0] for ch in word]
            gap = spacing * size * 0.5
            total = sum(widths) + gap * (len(word) - 1)
            bb = f.getbbox(word)
            if total <= 0.9 * w and bb[3] - bb[1] <= 0.85 * h:
                break
            size = int(size * 0.88)
        pil = Image.fromarray(cv2.cvtColor(roi, cv2.COLOR_BGR2RGB))
        d = ImageDraw.Draw(pil)
        sw = max(1, int(round(size * rng.uniform(0.04, 0.09))))
        x = (w - total) / 2
        top = f.getbbox(word)[1]
        y = (h - (f.getbbox(word)[3] - top)) / 2 - top
        shadow = rng.random() < 0.5
        rgb_fill, rgb_stroke = fill[::-1], stroke[::-1]
        for ch, cw in zip(word, widths):
            x0 = x - f.getbbox(ch)[0]
            if shadow:
                d.text((x0 + sw, y + sw), ch, font=f, fill=rgb_stroke)
            d.text((x0, y), ch, font=f, fill=rgb_fill, stroke_width=sw, stroke_fill=rgb_stroke)
            x += cw + gap
        roi[:] = cv2.cvtColor(np.asarray(pil), cv2.COLOR_RGB2BGR)
    else:  # Hershey fallback
        face = cv2.FONT_HERSHEY_DUPLEX
        scale = cv2.getFontScaleFromHeight(face, target_h, 3)
        (tw, th), _ = cv2.getTextSize(word, face, scale, 3)
        if tw > 0.9 * w:
            scale *= 0.9 * w / tw
            (tw, th), _ = cv2.getTextSize(word, face, scale, 3)
        org = (int((w - tw) / 2), int((h + th) / 2))
        cv2.putText(roi, word, org, face, scale, stroke, 5, cv2.LINE_AA)
        cv2.putText(roi, word, org, face, scale, fill, 2, cv2.LINE_AA)


def randomize_text(rng, rgba, cfg):
    """Replace / blur / erase the word inside the banner of a number card (copy returned)."""
    t = cfg["text"]
    h, w = rgba.shape[:2]
    x0, y0, x1, y1 = (int(round(v * s)) for v, s in zip(t["inner"], (w, h, w, h)))
    out = rgba.copy()
    roi = np.ascontiguousarray(out[y0:y1, x0:x1, :3])
    mode = pick(rng, t["modes"])
    if mode == "blur":
        roi = cv2.GaussianBlur(roi, (0, 0), U(rng, t["blur_sigma"]))
    else:
        # erase the printed word: inpaint everything that is not banner cream
        lab = cv2.cvtColor(roi, cv2.COLOR_BGR2LAB).astype(np.float32)
        cream = np.median(lab.reshape(-1, 3), axis=0)
        dist = np.linalg.norm(lab - cream, axis=2)
        m = (dist > 18).astype(np.uint8) * 255
        m = cv2.dilate(m, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7)))
        roi = cv2.inpaint(roi, m, 5, cv2.INPAINT_TELEA)
        if mode == "word":
            render_word(rng, roi, fake_word(rng, cfg))
            roi = cv2.GaussianBlur(roi, (0, 0), rng.uniform(0.5, 1.2))
    out[y0:y1, x0:x1, :3] = roi
    return out


# ---------------------------------------------------------------- layouts
# A card placement is (cls, cx, cy, width_px, angle_deg) in top-down scene coordinates;
# lists are in z order (bottom first).


def sample_class(rng):
    return G["classes"][rng.integers(len(G["classes"]))]


def make_row(rng, c, aspect, w, h, center, theta, n, card_w=None, span=None):
    d = np.array([np.cos(np.deg2rad(theta)), np.sin(np.deg2rad(theta))])
    nrm = np.array([-d[1], d[0]])
    if rng.random() < c["p_overlap"]:
        s = -U(rng, c["overlap"])
    else:
        s = U(rng, c["gap"])
    if card_w is None:
        ext = min(w / max(abs(d[0]), 1e-6), h / max(abs(d[1]), 1e-6))
        length = span * ext
        card_w = length / (n + (n - 1) * s)
        card_w = min(card_w, c["max_card_long"] * min(w, h) * aspect)
        card_w = max(card_w, c["min_card_w"])
    steps = [0.0]
    for _ in range(n - 1):
        steps.append(steps[-1] + card_w * (1 + s + rng.uniform(-1, 1) * c["spacing_jitter"]))
    steps = np.array(steps) - steps[-1] / 2
    cards = []
    for t in steps:
        off = rng.uniform(-1, 1, 2) * c["offset_jitter"] * card_w
        p = center + d * (t + off[0]) + nrm * off[1]
        ang = theta + rng.uniform(-c["rot_jitter"], c["rot_jitter"])
        if rng.random() < c["p_card_flip180"]:
            ang += 180
        cards.append([sample_class(rng), p[0], p[1], card_w, ang])
    if rng.random() < 0.5:
        cards.reverse()  # which neighbour lies on top when overlapping
    return cards, card_w


def layout_row(rng, cfg, aspect, w, h):
    c = cfg["row"]
    n = RI(rng, c["n_cards"])
    theta = rng.uniform(0, 360)
    center = np.array([w / 2, h / 2]) + rng.uniform(-1, 1, 2) * c["center_jitter"] * np.array([w, h])
    cards, cw = make_row(rng, c, aspect, w, h, center, theta, n, span=U(rng, c["span"]))
    if rng.random() < c["p_neighbour"]:
        ch = cw / aspect
        nrm = np.array([-np.sin(np.deg2rad(theta)), np.cos(np.deg2rad(theta))])
        side = 1 if rng.random() < 0.5 else -1
        nrm = nrm * side
        cw2 = cw * U(rng, c["neighbour_scale"])
        ch2 = cw2 / aspect
        edge = abs(w / 2 * nrm[0]) + abs(h / 2 * nrm[1])
        dist = edge + U(rng, c["neighbour_edge_offset"]) * ch2
        rel = np.dot(np.array([w / 2, h / 2]) + nrm * dist - center, nrm)
        dist += max(0.0, 1.1 * (ch + ch2) / 2 - rel)
        d = np.array([nrm[1], -nrm[0]])
        nc = np.array([w / 2, h / 2]) + nrm * dist + d * rng.uniform(-0.3, 0.3) * min(w, h)
        th2 = theta + rng.uniform(-c["neighbour_angle"], c["neighbour_angle"]) + (180 if rng.random() < 0.5 else 0)
        nb, _ = make_row(rng, c, aspect, w, h, nc, th2, RI(rng, c["neighbour_n"]), card_w=cw2)
        cards = nb + cards
    return cards


def layout_scattered(rng, cfg, aspect, w, h):
    c = cfg["scattered"]
    n = RI(rng, c["n_cards"])
    cw = U(rng, c["card_w"]) * min(w, h)
    m = c["margin"]
    cards, centers = [], []
    for _ in range(n):
        for _ in range(60):
            p = np.array([rng.uniform(-m, 1 + m) * w, rng.uniform(-m, 1 + m) * h])
            if all(np.linalg.norm(p - q) >= (1 - c["max_overlap"]) * 1.2 * cw / aspect for q in centers):
                centers.append(p)
                cards.append([sample_class(rng), p[0], p[1], cw * rng.uniform(0.9, 1.1), rng.uniform(0, 360)])
                break
    return cards


def layout_pile(rng, cfg, aspect, w, h):
    c = cfg["pile"]
    cards = []
    for _ in range(RI(rng, c["n_piles"])):
        n = RI(rng, c["n_cards"])
        cw = U(rng, c["card_w"]) * min(w, h)
        ch = cw / aspect
        center = np.array([rng.uniform(0.2, 0.8) * w, rng.uniform(0.2, 0.8) * h])
        base = rng.uniform(0, 360)
        if rng.random() < c["p_fan"]:
            step = U(rng, c["fan_step"])
            for i in range(n):
                ang = base + (i - (n - 1) / 2) * step
                p = center + apply_h(R(ang), [[0, -0.35 * ch]])[0]
                cards.append([sample_class(rng), p[0], p[1], cw, ang])
        else:
            for _ in range(n):
                p = center + rng.normal(0, c["pile_offset"], 2) * cw
                cards.append([sample_class(rng), p[0], p[1], cw, base + rng.uniform(-40, 40)])
    return cards


def layout_large(rng, cfg, aspect, w, h):
    c = cfg["large"]
    n = RI(rng, c["n_cards"])
    ch = U(rng, c["card_h"]) * min(w, h)
    cw = ch * aspect
    theta = rng.uniform(0, 360)
    d = np.array([np.cos(np.deg2rad(theta)), np.sin(np.deg2rad(theta))])
    center = np.array([w / 2, h / 2]) + rng.uniform(-1, 1, 2) * c["center_jitter"] * np.array([w, h])
    step = cw * (1 + U(rng, c["gap"]))
    cards = []
    for i in range(n):
        p = center + d * (i - (n - 1) / 2) * step
        cards.append([sample_class(rng), p[0], p[1], cw, theta + rng.uniform(-c["rot"], c["rot"])])
    return cards


LAYOUTS = {"row": layout_row, "scattered": layout_scattered, "pile": layout_pile, "large": layout_large}


# ---------------------------------------------------------------- rendering helpers


def background(rng, cfg, hs, w, h):
    """Warp a random crop/zoom/rotation/flip of a background photo into the final image."""
    b = cfg["background"]
    photo = cv2.imread(str(G["bgs"][rng.integers(len(G["bgs"]))]), cv2.IMREAD_COLOR)
    if rng.random() < b["p_flip"]:
        photo = cv2.flip(photo, 1)
    ph, pw = photo.shape[:2]
    corners = np.array([[0, 0], [w, 0], [w, h], [0, h]], np.float64)
    fp = apply_h(np.linalg.inv(hs), corners)  # frame footprint in scene coords
    beta = rng.uniform(0, 360)
    k = U(rng, b["zoom"])
    rot = apply_h(R(beta), fp - fp.mean(axis=0))
    ext = rot.max(axis=0) - rot.min(axis=0)
    fit = min(pw / ext[0], ph / ext[1])
    if k > fit:
        k = max(b["min_zoom"], fit)
    if k > 1.3:  # minify through the pyramid to avoid aliasing
        photo = cv2.pyrDown(photo)
        ph, pw = photo.shape[:2]
        k /= 2
    lo, hi = rot.min(axis=0) * k, rot.max(axis=0) * k
    pc = []
    for a, bnd, size in ((lo[0], hi[0], pw), (lo[1], hi[1], ph)):
        if bnd - a < size:
            pc.append(rng.uniform(-a, max(-a, size - bnd)))
        else:
            pc.append((size - (a + bnd)) / 2)
    bmat = T(pc[0], pc[1]) @ S(k) @ R(beta) @ T(*(-fp.mean(axis=0)))  # scene -> photo
    m = hs @ np.linalg.inv(bmat)
    return cv2.warpPerspective(photo, m, (w, h), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT_101), photo


def card_appearance(rng, cfg, rgb):
    """Per-card brightness/colour jitter and optional glare (rgb float32, in place)."""
    c = cfg["card"]
    gain = U(rng, c["brightness"]) * (1 + rng.uniform(-1, 1, 3) * c["color_jitter"])
    rgb *= gain.astype(np.float32)
    if rng.random() < c["p_glare"]:
        h, w = rgb.shape[:2]
        g = np.zeros((h, w), np.float32)
        r = U(rng, c["glare_size"]) * w
        cv2.ellipse(g, (int(rng.uniform(0, w)), int(rng.uniform(0, h))),
                    (int(r), int(r * rng.uniform(0.3, 1.0))), rng.uniform(0, 180), 0, 360, 1.0, -1)
        g = soft_blur(g, max(1.0, r * 0.4)) * U(rng, c["glare_strength"])
        rgb += (255 - rgb) * g[..., None]


def capsule_mask(shape, p0, p1, width):
    m = np.zeros(shape, np.uint8)
    cv2.line(m, tuple(int(v) for v in p0), tuple(int(v) for v in p1), 255, max(1, int(width)), cv2.LINE_AA)
    return m


def draw_occluder(rng, cfg, img, target, card_w, photo):
    """Draw one occluder near `target` (x, y) into img (float32). Returns its bool mask."""
    o = cfg["occluders"]
    h, w = img.shape[:2]
    kind = pick(rng, o["weights"])
    ang = rng.uniform(0, 2 * np.pi)
    d = np.array([np.cos(ang), np.sin(ang)])
    tgt = np.asarray(target) + rng.normal(0, 0.3, 2) * card_w
    if kind == "finger":
        fw = U(rng, o["finger_w"]) * card_w
        tip = tgt
        # fingers reach in from the nearest image edge (roughly)
        dists = {(-1, 0): tip[0], (1, 0): w - tip[0], (0, -1): tip[1], (0, 1): h - tip[1]}
        edge = np.array(min(dists, key=dists.get), np.float64)
        ang = np.arctan2(edge[1], edge[0]) + rng.uniform(-0.6, 0.6)
        d = np.array([np.cos(ang), np.sin(ang)])
        base = tip + d * 2 * max(h, w)
        nrm = np.array([-d[1], d[0]])
        m = capsule_mask((h, w), tip, base, fw).astype(np.float32) / 255
        nails = [(tip, fw)]
        for k in range(int(rng.choice([0, 0, 1, 2, 3]))):  # more fingers of the same hand
            side = 1 if k % 2 == 0 else -1
            off = nrm * fw * 1.05 * side * (1 + k // 2)
            back = d * fw * rng.uniform(0.3, 1.2) * (1 + k // 2)
            fwk = fw * rng.uniform(0.85, 1.0)
            m = np.maximum(m, capsule_mask((h, w), tip + off + back, base + off, fwk) / 255.0)
            nails.append((tip + off + back, fwk))
        col = np.array(SKIN_TONES[rng.integers(len(SKIN_TONES))], np.float32) * rng.uniform(0.85, 1.1)
        dist = cv2.distanceTransform((m > 0.5).astype(np.uint8), cv2.DIST_L2, 3)
        shade = 0.7 + 0.3 * np.clip(dist / (fw * 0.35 + 1e-6), 0, 1)
        tex = col[None, None, :] * shade[..., None]
        # low-frequency skin blotches + knuckle creases
        blot = soft_blur(rng.normal(0, 1, (h, w)).astype(np.float32), fw * 0.4)
        blot /= blot.std() + 1e-6
        tex *= (1 + 0.04 * blot)[..., None]
        for p, fwk in nails:
            for kd in (rng.uniform(0.9, 1.2), rng.uniform(1.9, 2.4)):
                c = p + d * fwk * kd
                q = np.array([-d[1], d[0]]) * fwk * 0.35
                cv2.line(tex, tuple((c - q).astype(int)), tuple((c + q).astype(int)), tuple(float(v) * 0.82 for v in col),
                         max(1, int(fwk * 0.06)), cv2.LINE_AA)
        if rng.random() < 0.6:  # finger nails
            nm = np.zeros((h, w), np.float32)
            ang_deg = float(np.rad2deg(ang))
            for p, fwk in nails:
                c = p + d * fwk * 0.32
                cv2.ellipse(nm, (int(c[0]), int(c[1])), (max(1, int(fwk * 0.3)), max(1, int(fwk * 0.36))), ang_deg,
                            0, 360, 1.0, -1, cv2.LINE_AA)
            nm = soft_blur(nm, max(0.5, fw * 0.04)) * 0.45
            tex = tex + (np.array([215, 215, 245], np.float32) - tex) * nm[..., None]
        # soft contact shadow
        sh = soft_blur(m, fw * 0.3)
        sh = np.roll(sh, (int(fw * 0.15), int(fw * 0.15)), axis=(0, 1))
        img *= (1 - 0.35 * sh)[..., None]
    elif kind == "patch":
        sz = U(rng, o["patch_size"]) * card_w
        pts = tgt + rng.normal(0, 0.5, (7, 2)) * sz
        m = np.zeros((h, w), np.uint8)
        cv2.fillPoly(m, [cv2.convexHull(pts.astype(np.int32))], 255, cv2.LINE_AA)
        m = m.astype(np.float32) / 255
        sc = rng.uniform(0.5, 1.5)
        src = cv2.resize(photo, None, fx=sc, fy=sc, interpolation=cv2.INTER_AREA)
        if src.shape[0] < h or src.shape[1] < w:
            src = cv2.resize(src, (max(w, src.shape[1]), max(h, src.shape[0])))
        y0, x0 = rng.integers(0, src.shape[0] - h + 1), rng.integers(0, src.shape[1] - w + 1)
        tex = src[y0 : y0 + h, x0 : x0 + w].astype(np.float32)
    else:  # pen
        L, pw_ = U(rng, o["pen_len"]) * card_w, U(rng, o["pen_w"]) * card_w
        p0, p1 = tgt - d * L / 2, tgt + d * L / 2
        m = np.zeros((h, w), np.uint8)
        nrm = np.array([-d[1], d[0]]) * pw_ / 2
        body = np.array([p0 + nrm, p1 - d * pw_ + nrm, p1, p1 - d * pw_ - nrm, p0 - nrm])
        cv2.fillPoly(m, [body.astype(np.int32)], 255, cv2.LINE_AA)
        m = m.astype(np.float32) / 255
        col = np.array(PEN_COLORS[rng.integers(len(PEN_COLORS))], np.float32)
        tex = np.empty((h, w, 3), np.float32)
        tex[:] = col
        hl = capsule_mask((h, w), p0 + nrm * 0.3, p1 - d * pw_ + nrm * 0.3, max(1, pw_ * 0.2)).astype(np.float32) / 255
        tex += (255 - tex) * 0.4 * hl[..., None]
        sh = soft_blur(m, max(1.0, pw_ * 0.5))
        sh = np.roll(sh, (int(pw_ * 0.4), int(pw_ * 0.4)), axis=(0, 1))
        img *= (1 - 0.4 * sh)[..., None]
    img[:] = img * (1 - m[..., None]) + tex * m[..., None]
    return m >= 0.5


def soft_shadow(rng, cfg, h, w):
    """Large soft multiplicative shadow mask in [0,1]: gradient, blob, hand or phone."""
    kind = rng.choice(["gradient", "blob", "hand", "phone"], p=[0.3, 0.2, 0.3, 0.2])
    m = np.zeros((h, w), np.float32)
    s = min(h, w)
    if kind == "gradient":
        a = rng.uniform(0, 2 * np.pi)
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        t = ((xx - w / 2) * np.cos(a) + (yy - h / 2) * np.sin(a)) / (0.5 * max(h, w))
        m = np.clip((t - rng.uniform(-0.5, 0.5)) / rng.uniform(0.2, 0.8), 0, 1)
        return m
    c = np.array([rng.uniform(0, w), rng.uniform(0, h)])
    if kind == "blob":
        cv2.ellipse(m, tuple(c.astype(int)), (int(s * rng.uniform(0.1, 0.4)), int(s * rng.uniform(0.1, 0.4))),
                    rng.uniform(0, 180), 0, 360, 1.0, -1)
    elif kind == "hand":
        a = rng.uniform(0, 360)
        palm = s * rng.uniform(0.15, 0.3)
        cv2.ellipse(m, tuple(c.astype(int)), (int(palm), int(palm * 0.8)), a, 0, 360, 1.0, -1)
        for k in range(4):
            fa = np.deg2rad(a - 30 + k * 20)
            p0 = c + np.array([np.cos(fa), np.sin(fa)]) * palm * 0.7
            p1 = c + np.array([np.cos(fa), np.sin(fa)]) * palm * rng.uniform(1.6, 2.1)
            cv2.line(m, tuple(p0.astype(int)), tuple(p1.astype(int)), 1.0, int(palm * 0.3))
        # arm leaving the frame
        p1 = c - np.array([np.cos(np.deg2rad(a)), np.sin(np.deg2rad(a))]) * s * 2
        cv2.line(m, tuple(c.astype(int)), tuple(p1.astype(int)), 1.0, int(palm * 1.1))
    else:  # phone held above the table
        pw_, ph_ = s * rng.uniform(0.25, 0.45), s * rng.uniform(0.5, 0.9)
        box = cv2.boxPoints(((float(c[0]), float(c[1])), (pw_, ph_), rng.uniform(0, 180)))
        cv2.fillPoly(m, [box.astype(np.int32)], 1.0)
    return soft_blur(m, s * rng.uniform(0.02, 0.08))


def photometric(rng, cfg, img):
    p = cfg["photo"]
    h, w = img.shape[:2]
    if rng.random() < p["p_shadow"]:
        img *= (1 - U(rng, p["shadow_strength"]) * soft_shadow(rng, cfg, h, w))[..., None]
    if rng.random() < p["p_glare"]:
        g = np.zeros((h, w), np.float32)
        r = min(h, w) * rng.uniform(0.03, 0.15)
        cv2.ellipse(g, (int(rng.uniform(0, w)), int(rng.uniform(0, h))), (int(r), int(r * rng.uniform(0.3, 1))),
                    rng.uniform(0, 180), 0, 360, 1.0, -1)
        g = soft_blur(g, r * 0.5) * rng.uniform(0.3, 0.85)
        img += (255 - img) * g[..., None]
    t = U(rng, p["temperature"]) * p["temp_gain"]
    img *= np.array([1 - t, 1 - 0.1 * abs(t), 1 + t], np.float32)  # BGR: warm = more red, less blue
    img *= U(rng, p["brightness"])
    mean = float(img.mean())
    img[:] = (img - mean) * U(rng, p["contrast"]) + mean
    gray = img.mean(axis=2, keepdims=True)
    img[:] = gray + (img - gray) * U(rng, p["saturation"])
    if rng.random() < p["p_vignette"]:
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        r2 = ((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2
        img *= (1 - U(rng, p["vignette"]) * np.clip(r2 / 2, 0, 1))[..., None]
    if rng.random() < p["p_defocus"]:
        img[:] = cv2.GaussianBlur(img, (0, 0), U(rng, p["defocus_sigma"]))
    if rng.random() < p["p_motion"]:
        n = RI(rng, p["motion_len"])
        k = np.zeros((n, n), np.float32)
        k[n // 2, :] = 1
        k = cv2.warpAffine(k, cv2.getRotationMatrix2D((n / 2 - 0.5, n / 2 - 0.5), rng.uniform(0, 180), 1), (n, n))
        img[:] = cv2.filter2D(img, -1, k / max(k.sum(), 1e-6))
    sigma = U(rng, p["noise_sigma"])
    if sigma > 0:
        noise = rng.normal(0, sigma, (h, w, 1)).astype(np.float32)
        if rng.random() < p["p_chroma_noise"]:
            noise = noise + rng.normal(0, sigma * 0.5, (h, w, 3)).astype(np.float32)
        img += noise
    return img


# ---------------------------------------------------------------- one image


def generate(task):
    idx, seed, split, out = task
    cfg = G["cfg"]
    rng = np.random.default_rng([seed, idx])

    # image size
    ic = cfg["image"]
    long_ = RI(rng, ic["long_side"])
    asp = pick(rng, ic["aspects"])
    a, b = (int(v) for v in asp.split(":"))
    short = int(round(long_ * a / b / 2)) * 2
    w, h = (short, long_) if rng.random() < ic["p_portrait"] else (long_, short)

    ref = G["cutouts"][G["classes"][0]][0]
    aspect = ref.shape[1] / ref.shape[0]  # card w / h
    mode = pick(rng, cfg["layout_weights"])
    cards = LAYOUTS[mode](rng, cfg, aspect, w, h)

    hs = camera_homography(rng, cfg, w, h)
    bg, photo = background(rng, cfg, hs, w, h)
    img = bg.astype(np.float32)

    # global light direction for card drop shadows
    la = rng.uniform(0, 2 * np.pi)
    ldir = np.array([np.cos(la), np.sin(la)])
    cc = cfg["card"]
    shadow_k = (U(rng, cc["shadow_strength"]), U(rng, cc["shadow_offset"]), U(rng, cc["shadow_blur"]))
    thr = cfg["labels"]["alpha_threshold"]

    rendered = []  # (cls, x0, y0, mask, full_area, center_xy, width_px)
    for cls, cx, cy, cw_scene, ang in cards:
        variants = G["cutouts"][cls]
        rgba = variants[rng.integers(len(variants))]
        if cls in G["number"] and rng.random() < cfg["text"]["p"]:
            rgba = randomize_text(rng, rgba, cfg)
        ch0, cw0 = rgba.shape[:2]
        a_mat = T(cx, cy) @ R(ang) @ S(cw_scene / cw0) @ T(-cw0 / 2, -ch0 / 2)
        hf = hs @ a_mat
        corners0 = np.array([[0, 0, 1], [cw0, 0, 1], [cw0, ch0, 1], [0, ch0, 1]], np.float64)
        if (corners0 @ hf.T)[:, 2].min() <= 1e-6:  # (partly) beyond the horizon: not in view
            continue
        quad = apply_h(hf, corners0[:, :2])
        edges = np.linalg.norm(np.roll(quad, -1, axis=0) - quad, axis=1)
        smax = max(edges[0] / cw0, edges[2] / cw0, edges[1] / ch0, edges[3] / ch0)
        r = min(1.0, smax * 1.25)
        if r < 1.0:
            rgba = cv2.resize(rgba, (max(2, int(round(cw0 * r))), max(2, int(round(ch0 * r)))), interpolation=cv2.INTER_AREA)
            hf = hf @ S(cw0 / rgba.shape[1], ch0 / rgba.shape[0])
        rgb = rgba[..., :3].astype(np.float32)
        card_appearance(rng, cfg, rgb)
        rgba = np.dstack([np.clip(rgb, 0, 255), rgba[..., 3].astype(np.float32)])
        area_full = poly_area(quad)
        width_px = np.sqrt(area_full * aspect)
        margin = int(width_px * (shadow_k[1] + 3 * shadow_k[2])) + 2
        x0 = max(0, int(np.floor(quad[:, 0].min())) - margin)
        y0 = max(0, int(np.floor(quad[:, 1].min())) - margin)
        x1 = min(w, int(np.ceil(quad[:, 0].max())) + margin + 1)
        y1 = min(h, int(np.ceil(quad[:, 1].max())) + margin + 1)
        center = apply_h(hf, [[rgba.shape[1] / 2, rgba.shape[0] / 2]])[0]
        if x1 - x0 < 2 or y1 - y0 < 2:
            rendered.append((cls, 0, 0, None, area_full, center, width_px))
            continue
        warped = cv2.warpPerspective(rgba, T(-x0, -y0) @ hf, (x1 - x0, y1 - y0), flags=cv2.INTER_LINEAR,
                                     borderMode=cv2.BORDER_CONSTANT, borderValue=0)
        alpha = warped[..., 3] / 255.0
        sh = soft_blur(alpha, width_px * shadow_k[2])
        off = (ldir * width_px * shadow_k[1]).round().astype(int)
        sh = np.roll(sh, (off[1], off[0]), axis=(0, 1))
        roi = img[y0:y1, x0:x1]
        roi *= (1 - shadow_k[0] * sh)[..., None]
        roi[:] = roi * (1 - alpha[..., None]) + warped[..., :3] * alpha[..., None]
        rendered.append((cls, x0, y0, alpha >= thr, area_full, center, width_px))

    # occluders on top of everything
    occupied = np.zeros((h, w), bool)
    oc = cfg["occluders"]
    inside = [r_ for r_ in rendered if r_[3] is not None]
    if inside and rng.random() < oc["p_image"]:
        for _ in range(RI(rng, oc["n"])):
            t = inside[rng.integers(len(inside))]
            occupied |= draw_occluder(rng, cfg, img, t[5], t[6], photo)

    # visible masks, top card first
    lab = cfg["labels"]
    labels, dropped = [], 0
    for cls, x0, y0, mask, area_full, _, _ in reversed(rendered):
        if mask is None:
            dropped += 1
            continue
        mh, mw = mask.shape
        occ = occupied[y0 : y0 + mh, x0 : x0 + mw]
        vis = mask & ~occ
        occ |= mask
        n_vis = int(vis.sum())
        if n_vis < lab["min_visible"] * area_full:
            dropped += 1
            continue
        ys = np.flatnonzero(vis.any(axis=1))
        xs = np.flatnonzero(vis.any(axis=0))
        bx0, bx1 = x0 + xs[0], x0 + xs[-1] + 1
        by0, by1 = y0 + ys[0], y0 + ys[-1] + 1
        if min(bx1 - bx0, by1 - by0) < lab["min_box"]:
            dropped += 1
            continue
        labels.append((G["classes"].index(cls), bx0, by0, bx1, by1))

    img = photometric(rng, cfg, img)
    q = RI(rng, cfg["jpeg_quality"])
    ok, buf = cv2.imencode(".jpg", np.clip(img, 0, 255).astype(np.uint8), [cv2.IMWRITE_JPEG_QUALITY, q])
    if not ok:
        raise RuntimeError("jpeg encode failed")
    name = f"{idx:06d}"
    (out / "images" / split / f"{name}.jpg").write_bytes(buf.tobytes())
    with open(out / "labels" / split / f"{name}.txt", "w") as f:
        for c, bx0, by0, bx1, by1 in labels:
            f.write(f"{c} {(bx0 + bx1) / 2 / w:.6f} {(by0 + by1) / 2 / h:.6f} {(bx1 - bx0) / w:.6f} {(by1 - by0) / h:.6f}\n")
    return idx, mode, [c for c, *_ in labels], len(cards), dropped


# ---------------------------------------------------------------- main


def prepare_out(out: Path):
    """Wipe `out` if it is ours (has the marker) or empty; refuse anything else."""
    out = out.resolve()
    protected = {Path("/"), Path.home().resolve(), ML, ML.parent, ML / "out", ML / "data", ML / "config", ML / "scripts"}
    if out in protected or out in ML.parents:
        raise SystemExit(f"refusing to use {out} as output dir")
    if out.exists():
        if not out.is_dir():
            raise SystemExit(f"{out} exists and is not a directory")
        if any(out.iterdir()) and not (out / MARKER).exists():
            raise SystemExit(f"{out} is not empty and was not created by gen_synth.py; refusing to wipe it")
        shutil.rmtree(out)
    for split in ("train", "val"):
        (out / "images" / split).mkdir(parents=True)
        (out / "labels" / split).mkdir(parents=True)
    (out / MARKER).write_text("created by ml/scripts/gen_synth.py; this directory is wiped on every run\n")
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--count", type=int, default=None, help="number of images (default: synth.yaml count)")
    ap.add_argument("--val-frac", type=float, default=None, help="validation fraction (default 0.1)")
    ap.add_argument("--seed", type=int, default=None, help="random seed (default: synth.yaml seed)")
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT, help=f"output dir (default {DEFAULT_OUT})")
    ap.add_argument("--workers", type=int, default=max(1, (mp.cpu_count() or 2) - 1))
    ap.add_argument("--config", type=Path, default=SYNTH_CONFIG)
    args = ap.parse_args()

    cfg = load_yaml(args.config)
    cards_cfg = load_yaml(CARDS_CONFIG)
    classes = cards_cfg["classes"]
    count = args.count if args.count is not None else int(cfg["count"])
    val_frac = args.val_frac if args.val_frac is not None else float(cfg.get("val_frac", 0.1))
    seed = args.seed if args.seed is not None else int(cfg["seed"])

    load_cutouts(classes)  # fail early
    bgs = prepare_backgrounds(cfg, args.workers)
    out = prepare_out(args.out)

    n_val = int(round(count * val_frac))
    val = set(np.random.default_rng(seed).permutation(count)[:n_val].tolist())
    tasks = [(i, seed, "val" if i in val else "train", out) for i in range(count)]

    t0 = time.time()
    hist, modes = Counter(), Counter()
    placed = dropped = 0
    ctx = mp.get_context("spawn")
    with ctx.Pool(args.workers, initializer=init_worker, initargs=(cfg, cards_cfg, bgs)) as pool:
        for k, (_, mode, cls_ids, n_cards, n_drop) in enumerate(pool.imap_unordered(generate, tasks, chunksize=2), 1):
            hist.update(cls_ids)
            modes[mode] += 1
            placed += n_cards
            dropped += n_drop
            if k % 100 == 0 or k == count:
                el = time.time() - t0
                print(f"  {k}/{count}  {k / el:.1f} img/s", flush=True)
    el = time.time() - t0

    ds = {"path": str(out), "train": "images/train", "val": "images/val", "names": dict(enumerate(classes))}
    with open(out / "dataset.yaml", "w") as f:
        f.write("# generated by scripts/gen_synth.py\n")
        yaml.safe_dump(ds, f, sort_keys=False, allow_unicode=True)

    total = sum(hist.values())
    print(f"\n{count} images ({count - n_val} train / {n_val} val) in {el:.1f}s = {count / el:.1f} img/s "
          f"with {args.workers} workers -> {out}")
    print("layouts: " + ", ".join(f"{m} {n}" for m, n in modes.most_common()))
    print(f"cards placed {placed}, labeled {total}, dropped (hidden/outside/tiny) {dropped}")
    print("per-class instances:")
    mx = max(hist.values()) if hist else 1
    for i, c in enumerate(classes):
        n = hist.get(i, 0)
        print(f"  {i:2d} {c:14s} {n:6d}  {'#' * int(round(40 * n / mx))}")
    if hist:
        v = np.array([hist.get(i, 0) for i in range(len(classes))])
        print(f"  min {v.min()}  max {v.max()}  mean {v.mean():.1f}  (max/min {v.max() / max(v.min(), 1):.2f})")


if __name__ == "__main__":
    main()
