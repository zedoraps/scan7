"""Stage 1: cut the cards in ml/data/cards_flat/ out of their photos.

For every photo: find the card against the dark mat, fit its 4 edges (ignoring the rounded
corners), perspective-correct it to an upright portrait rectangle, apply the per-file rotation
from config/cards.yaml, and save an RGBA PNG with an anti-aliased rounded-corner alpha mask
to ml/out/cutouts/<stem>.png. Also writes ml/out/cutouts/contact_sheet.jpg.

Naming: a photo stem is a class name (`12`) or `<class>_<n>` (`12_2`) for another photo of the
same class. Extra looks of a class that do not come from a photo (e.g. English-edition variants
cut from a render, see scripts/extract_en_variants.py) are stored already rectified as RGBA
600x832 PNGs in ml/data/cards_extra/<class>_<description>.png (e.g. `00_en_rainbow.png`,
`back_en.png`). They are copied unchanged to out/cutouts/<class>_<n>.png, n = the next free
number of that class after the photos (sorted by file name), so the generator picks them like any
other variant. out/cutouts/sources.yaml maps every cutout stem to its source file; synth.yaml
`variants:` refers to the source stems (e.g. `00_en_rainbow`), not to the numbered cutout stems.

    uv run python scripts/make_cutouts.py            # cutouts + contact sheet
    uv run python scripts/make_cutouts.py --measure  # also print measured aspect / corner radius
"""

from __future__ import annotations

import argparse
from pathlib import Path

import cv2
import numpy as np
import yaml

ML = Path(__file__).resolve().parents[1]
CONFIG = ML / "config" / "cards.yaml"
SRC = ML / "data" / "cards_flat"
EXTRA = ML / "data" / "cards_extra"
OUT = ML / "out" / "cutouts"

WORK_LONG_SIDE = 2000  # detection resolution (long side, px)
EDGE_MARGIN = 2  # px (at cutout size) the alpha stays inside the detected card silhouette


def load_config() -> dict:
    with open(CONFIG) as f:
        return yaml.safe_load(f)


def extra_class(stem: str, classes: list[str]) -> str:
    """'00_en_rainbow' -> '00', 'back_en' -> 'back' (longest class name that prefixes the stem)."""
    hits = [c for c in classes if stem == c or stem.startswith(c + "_")]
    if not hits:
        raise ValueError(f"cards_extra/{stem}.png: name must start with a class name + '_'")
    return max(hits, key=len)


def class_of(stem: str, classes: list[str]) -> str:
    """'12_2' -> '12', 'second_chance' -> 'second_chance'."""
    if stem in classes:
        return stem
    base, _, suffix = stem.rpartition("_")
    if base in classes and suffix.isdigit():
        return base
    raise ValueError(f"{stem}: not a known class name (see config/cards.yaml)")


# ---------------------------------------------------------------- detection


def card_mask(img: np.ndarray) -> np.ndarray:
    """Binary mask (convex) of the bright card on the dark mat."""
    v = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)[..., 2]
    v = cv2.GaussianBlur(v, (0, 0), 3)
    _, m = cv2.threshold(v, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (15, 15))
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, k)
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, k)
    contours, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    if not contours:
        raise RuntimeError("no card found")
    c = max(contours, key=cv2.contourArea)
    # The card is convex: the hull bridges dark print touching the edge (e.g. the shells on
    # number cards) that falls below the threshold.
    out = np.zeros_like(m)
    cv2.drawContours(out, [cv2.convexHull(c)], -1, 255, cv2.FILLED)
    return out


def order_corners(pts: np.ndarray) -> np.ndarray:
    """Order 4 points TL, TR, BR, BL (as seen in the image)."""
    c = pts.mean(axis=0)
    ang = np.arctan2(pts[:, 1] - c[1], pts[:, 0] - c[0])
    pts = pts[np.argsort(ang)]  # clockwise in image coords, starting near -180°
    # start at the point with the smallest x+y (top-left)
    i = int(np.argmin(pts.sum(axis=1)))
    return np.roll(pts, -i, axis=0)


def line_intersection(l1, l2) -> np.ndarray:
    (vx1, vy1, x1, y1), (vx2, vy2, x2, y2) = l1, l2
    a = np.array([[vx1, -vx2], [vy1, -vy2]])
    t = np.linalg.solve(a, np.array([x2 - x1, y2 - y1]))
    return np.array([x1 + t[0] * vx1, y1 + t[0] * vy1])


def find_corners(mask: np.ndarray) -> np.ndarray:
    """Sharp corners (intersection of the 4 fitted edge lines) of the rounded card."""
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    cnt = max(contours, key=cv2.contourArea)
    hull = cv2.convexHull(cnt)
    peri = cv2.arcLength(hull, True)
    approx = None
    for eps in np.linspace(0.005, 0.1, 60):
        a = cv2.approxPolyDP(hull, eps * peri, True)
        if len(a) == 4:
            approx = a.reshape(4, 2).astype(np.float64)
            break
    if approx is None:
        raise RuntimeError("could not approximate card as a quadrilateral")
    rough = order_corners(approx)

    pts = cnt.reshape(-1, 2).astype(np.float64)
    lines = []
    for i in range(4):
        p, q = rough[i], rough[(i + 1) % 4]
        d = q - p
        length = np.linalg.norm(d)
        u = d / length
        n = np.array([-u[1], u[0]])
        rel = pts - p
        t = rel @ u / length
        dist = np.abs(rel @ n)
        sel = pts[(t > 0.15) & (t < 0.85) & (dist < 0.03 * length)]
        if len(sel) < 20:
            raise RuntimeError("too few edge points")
        lines.append(cv2.fitLine(sel.astype(np.float32), cv2.DIST_HUBER, 0, 0.01, 0.01).ravel())
    return np.array([line_intersection(lines[i - 1], lines[i]) for i in range(4)])


# ---------------------------------------------------------------- rectification


def rotate_cw(img: np.ndarray, deg: int) -> np.ndarray:
    deg %= 360
    if deg == 0:
        return img
    code = {90: cv2.ROTATE_90_CLOCKWISE, 180: cv2.ROTATE_180, 270: cv2.ROTATE_90_COUNTERCLOCKWISE}
    if deg not in code:
        raise ValueError(f"rotation must be a multiple of 90, got {deg}")
    return cv2.rotate(img, code[deg])


def warp(img, corners, out_w, out_h, trim=0.0, interp=cv2.INTER_AREA):
    """Map the card quad (TL,TR,BR,BL) to an out_w x out_h image, cutting `trim` px per edge."""
    dst = np.array(
        [[-trim, -trim], [out_w - 1 + trim, -trim], [out_w - 1 + trim, out_h - 1 + trim], [-trim, out_h - 1 + trim]],
        dtype=np.float32,
    )
    h = cv2.getPerspectiveTransform(corners.astype(np.float32), dst)
    return cv2.warpPerspective(img, h, (out_w, out_h), flags=interp, borderMode=cv2.BORDER_REPLICATE)


def rounded_alpha(w: int, h: int, radius: float, ss: int = 4) -> np.ndarray:
    """Anti-aliased rounded-rectangle alpha mask (supersampled)."""
    W, H, r = w * ss, h * ss, radius * ss
    m = np.zeros((H, W), np.uint8)
    ri = int(round(r))
    cv2.rectangle(m, (ri, 0), (W - 1 - ri, H - 1), 255, cv2.FILLED)
    cv2.rectangle(m, (0, ri), (W - 1, H - 1 - ri), 255, cv2.FILLED)
    for cx, cy in [(ri, ri), (W - 1 - ri, ri), (W - 1 - ri, H - 1 - ri), (ri, H - 1 - ri)]:
        cv2.circle(m, (cx, cy), ri, 255, cv2.FILLED, lineType=cv2.LINE_AA)
    return cv2.resize(m, (w, h), interpolation=cv2.INTER_AREA)


def measure(corners: np.ndarray, mask: np.ndarray, rot: int) -> tuple[float, float]:
    """(aspect w/h, corner radius / width) of one card, in upright orientation."""
    side = [np.linalg.norm(corners[(i + 1) % 4] - corners[i]) for i in range(4)]
    w_img, h_img = (side[0] + side[2]) / 2, (side[1] + side[3]) / 2
    w, h = (h_img, w_img) if rot % 180 else (w_img, h_img)
    aspect = w / h
    # Corner radius: rectify the mask at high res and walk the diagonal from each corner.
    mw, mh = 1000, int(round(1000 / aspect))
    if rot % 180:
        m = rotate_cw(warp(mask, corners, mh, mw, interp=cv2.INTER_LINEAR), rot)
    else:
        m = warp(mask, corners, mw, mh, interp=cv2.INTER_LINEAR)
    radii = []
    for flip_x in (False, True):
        for flip_y in (False, True):
            mm = m[:, ::-1] if flip_x else m
            mm = mm[::-1] if flip_y else mm
            diag = np.array([mm[i, i] for i in range(200)])
            d = int(np.argmax(diag > 127))
            radii.append(d / (np.sqrt(2) - 1) / mw)
    return aspect, float(np.median(radii))


# ---------------------------------------------------------------- contact sheet


def checkerboard(h, w, s=12):
    yy, xx = np.mgrid[0:h, 0:w]
    board = np.where(((yy // s) + (xx // s)) % 2 == 0, 150, 110).astype(np.uint8)
    return cv2.merge([board, board, board])


def contact_sheet(items, banner, number_classes, cols=6, cell_w=220):
    """items: list of (stem, class, rgba). Composite on checkerboard, label, banner outline."""
    h0, w0 = items[0][2].shape[:2]
    cell_h = int(round(cell_w * h0 / w0))
    pad, label_h = 12, 26
    rows = (len(items) + cols - 1) // cols
    sheet = np.full((rows * (cell_h + label_h + pad) + pad, cols * (cell_w + pad) + pad, 3), 70, np.uint8)
    for i, (stem, cls, rgba) in enumerate(items):
        r, c = divmod(i, cols)
        x, y = pad + c * (cell_w + pad), pad + r * (cell_h + label_h + pad)
        small = cv2.resize(rgba, (cell_w, cell_h), interpolation=cv2.INTER_AREA)
        a = small[..., 3:4].astype(np.float32) / 255
        bg = checkerboard(cell_h, cell_w)
        comp = (small[..., :3] * a + bg * (1 - a)).astype(np.uint8)
        if cls in number_classes:
            x0, y0, x1, y1 = banner
            cv2.rectangle(
                comp,
                (int(round(x0 * cell_w)), int(round(y0 * cell_h))),
                (int(round(x1 * cell_w)) - 1, int(round(y1 * cell_h)) - 1),
                (255, 0, 255),
                1,
            )
        sheet[y : y + cell_h, x : x + cell_w] = comp
        cv2.putText(sheet, stem, (x + 2, y + cell_h + 19), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 1, cv2.LINE_AA)
    return sheet


# ---------------------------------------------------------------- main


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--measure", action="store_true", help="print measured aspect ratio and corner radius")
    ap.add_argument("--debug", action="store_true", help="also save detection overlays to out/cutouts/debug/")
    args = ap.parse_args()

    cfg = load_config()
    classes = cfg["classes"]
    geo = cfg["geometry"]
    out_w = int(geo["cutout_width"])
    out_h = int(geo["cutout_height"]) or int(round(out_w / geo["aspect"]))
    radius = geo["corner_radius"] * out_w
    trim = float(cfg["cutouts"]["trim_px"])
    rotations = cfg["cutouts"].get("rotate") or {}
    alpha = rounded_alpha(out_w, out_h, radius)

    OUT.mkdir(parents=True, exist_ok=True)
    if args.debug:
        (OUT / "debug").mkdir(exist_ok=True)

    files = sorted(SRC.glob("*.jpg"))
    items, aspects, radii = [], [], []
    for f in files:
        stem = f.stem
        cls = class_of(stem, classes)
        img = cv2.imread(str(f), cv2.IMREAD_COLOR)
        if img is None:
            raise RuntimeError(f"cannot read {f}")
        scale = WORK_LONG_SIDE / max(img.shape[:2])
        small = cv2.resize(img, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
        mask = card_mask(small)
        corners_small = find_corners(mask)
        corners = corners_small / scale
        rot = int(rotations.get(stem, 0))

        # Rectify in photo orientation (landscape if the card lies sideways), then rotate upright.
        side = [np.linalg.norm(corners[(i + 1) % 4] - corners[i]) for i in range(4)]
        sideways = (side[0] + side[2]) > (side[1] + side[3])
        if sideways != bool(rot % 180):
            raise RuntimeError(f"{stem}: card lies {'sideways' if sideways else 'upright'} but rotation is {rot}")
        ww, hh = (out_h, out_w) if sideways else (out_w, out_h)
        rgb = rotate_cw(warp(img, corners, ww, hh, trim=trim), rot)
        # Bent cards are not perfectly straight: also clip the alpha with the detected card
        # silhouette (shrunk by a safety margin) so no mat pixels survive near the edges.
        sil = rotate_cw(warp(mask, corners_small, ww, hh, trim=trim, interp=cv2.INTER_LINEAR), rot)
        sil = cv2.erode(sil, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * EDGE_MARGIN + 1,) * 2))
        sil = cv2.GaussianBlur(sil, (0, 0), 0.7)
        a = np.minimum(alpha, sil)
        cut = int(np.count_nonzero(alpha.astype(int) - a > 127))
        rgba = np.dstack([rgb, a])
        cv2.imwrite(str(OUT / f"{stem}.png"), rgba)
        items.append((stem, cls, rgba))

        if args.measure:
            a, r = measure(corners_small, mask, rot)
            aspects.append(a)
            radii.append(r)
            print(f"{stem:15s} aspect {a:.4f}  corner_radius/w {r:.4f}")
        if args.debug:
            dbg = small.copy()
            cv2.polylines(dbg, [corners_small.astype(np.int32)], True, (0, 255, 0), 2)
            cv2.imwrite(str(OUT / "debug" / f"{stem}.jpg"), dbg)
        print(f"{stem:15s} -> {cls:14s} rot {rot:3d}  silhouette clipped {cut} px")

    # extra looks (already rectified RGBA) -> <class>_<n>
    sources = {stem: f"data/cards_flat/{stem}.jpg" for stem, _, _ in items}
    for stale in OUT.glob("*.png"):  # numbered cutouts from an earlier run that no source backs any more
        if stale.stem not in sources and stale.stem.rpartition("_")[2].isdigit():
            stale.unlink()
    for f in sorted(EXTRA.glob("*.png")) if EXTRA.is_dir() else []:
        cls = extra_class(f.stem, classes)
        rgba = cv2.imread(str(f), cv2.IMREAD_UNCHANGED)
        if rgba is None or rgba.ndim != 3 or rgba.shape[2] != 4 or rgba.shape[:2] != (out_h, out_w):
            raise RuntimeError(f"{f}: must be an RGBA {out_w}x{out_h} PNG")
        n = 2
        while f"{cls}_{n}" in sources:
            n += 1
        stem = f"{cls}_{n}"
        cv2.imwrite(str(OUT / f"{stem}.png"), rgba)
        sources[stem] = f"data/cards_extra/{f.name}"
        items.append((stem, cls, rgba))
        print(f"{stem:15s} -> {cls:14s} from cards_extra/{f.name}")
    with open(OUT / "sources.yaml", "w") as fh:
        fh.write("# written by scripts/make_cutouts.py: cutout stem -> source file (relative to ml/)\n")
        yaml.safe_dump(sources, fh, sort_keys=True)

    if args.measure:
        print(f"median aspect {np.median(aspects):.4f} (std {np.std(aspects):.4f}), "
              f"median corner radius/w {np.median(radii):.4f} (std {np.std(radii):.4f})")

    # contact sheet in class order
    order = {c: i for i, c in enumerate(classes)}
    items.sort(key=lambda t: (order[t[1]], t[0]))
    sheet = contact_sheet(items, cfg["banner"], set(cfg["number_classes"]))
    cv2.imwrite(str(OUT / "contact_sheet.jpg"), sheet, [cv2.IMWRITE_JPEG_QUALITY, 92])
    print(f"wrote {len(items)} cutouts + contact sheet to {OUT}")


if __name__ == "__main__":
    main()
