"""One-off: cut the English-edition looks of Flip 7: With a Vengeance out of web images and store
them as rectified RGBA sources in ml/data/cards_extra_vengeance/ (make_cutouts.py copies them into
out/cutouts_vengeance once cards_vengeance.yaml sets paths.extra to that dir).

    uv run python scripts/extract_vengeance_en_variants.py            # -> data/cards_extra_vengeance/*.png
    uv run python scripts/extract_vengeance_en_variants.py --sheet    # + contact_sheet.jpg there

Sources (ml/data/web_vengeance_en/images/, listed in sources.csv there; never used for evaluation):
  op_CardsAll_Retailer.jpg    publisher render of the whole English deck (3000x3000, white ground).
                              Cards are axis-aligned, 373x587 px, partly overlapping in rows/blocks.
  op_CardsSetup_Retailer.jpg  publisher render of a setup (2, 5, 9, 13, 10, Steal, -8), cards
                              ~469x735 px, isolated, rotated ~1 deg (4-point quads).
  bgg_9441556.png             BoardGameGeek comparison image: flat scans of the printed English Just
                              One More, Flip Four, Steal, Discard (~367x583, palette PNG).
  op_Showcase_1.jpg           publisher photo (2500 px), only used to fill the part of Swap that the
                              deck render hides.
  op_PR_Retailer.jpg          publisher render with a deck of backs (perspective), only used to fill
                              the strip of the back that card "1" hides in the deck render.
The renders are squashed horizontally (cards ~0.636 w/h instead of 0.719); rectifying to the
cards_vengeance.yaml cutout size (600x832) restores the real aspect.

Only cards whose English look differs from the German one are extracted: titles and rules text
(actions, modifiers, The Zero / Lucky / Unlucky), the back, and 6 / 9 (no dot after the digit on the
English print). The other number cards differ only in the banner word, which gen_synth randomizes.
All English cards also print "PRESS YOUR LUCK" / "THE OP GAMES" in the top / bottom ring (tiny).

Per card, pixels that are not the card's own print are replaced (cover rects in source px, applied
in order on the rectified cutout):
  mirror    strip covered by a neighbour -> mirrored from the other side of the card (number and
            modifier frames are left/right symmetric near the edges);
  template  modifier bottoms covered by a neighbour -> the same area of the ÷2 cutout (identical
            fan / ring / frame on every modifier);
  register  covered area -> another image of the same card, aligned with SIFT + RANSAC homography
            on the visible part, colour-matched to the render (quadratic colour map fitted next to
            the covered area, then the remaining low-frequency difference extrapolated into it).
The render's corners are rounder than the real card, so the white ground inside the rounded alpha
of cards_vengeance.yaml but outside the render silhouette is inpainted. The scans (BGG) are aligned
to the render cutout of the same card (homography), so they get the same geometry.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import cv2
import numpy as np

from make_cutouts import load_config, rounded_alpha

ML = Path(__file__).resolve().parents[1]
IMG = ML / "data" / "web_vengeance_en" / "images"
OUT = ML / "data" / "cards_extra_vengeance"
CONFIG = ML / "config" / "cards_vengeance.yaml"

SRC = {
    "all": IMG / "op_CardsAll_Retailer.jpg",
    "setup": IMG / "op_CardsSetup_Retailer.jpg",
    "bgg": IMG / "bgg_9441556.png",
    "photo": IMG / "op_Showcase_1.jpg",
    "deck": IMG / "op_PR_Retailer.jpg",
}
SHADOW = 14  # source px: the soft drop shadow a covering card throws on the card below
INF = 99999


def rect(x0, y0, x1, y1):
    return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]


def over(x0=-INF, y0=-INF, x1=INF, y1=INF, shadow=SHADOW):
    """Cover rect in source px for a neighbour whose edge is at x0/y0 (left/top) or x1/y1, incl. shadow.
    A smaller shadow keeps print that is only slightly darkened (e.g. the end of a title)."""
    return (x0 - shadow if x0 > -INF else -INF, y0 - shadow if y0 > -INF else -INF,
            x1 + shadow if x1 < INF else INF, y1 + shadow if y1 < INF else INF)


# Card quads (TL, TR, BR, BL) in source px. CardsAll rects: matched (gradient NCC) against the fully
# visible Unlucky 7 / ÷2 / Discard, whose edges were measured on the white ground (373x587 px).
# CardsSetup quads: minAreaRect of the card silhouette (saturation mask, shadow excluded).
# (name, source, quad, render?, [(fill, cover rect, extra)])
CARDS = [
    ("back_en_render", "all", rect(86.5, 250.5, 459.5, 837.5), True,
     [("register", over(x0=412.0, shadow=6), ("deck", (120, 1820, 740, 2460)))]),  # card "1" on its right
    ("06_en_render", "all", rect(1914.6, 250.5, 2287.6, 837.5), True,
     [("mirror", over(x0=2214.0), None)]),                                  # "7" on its right
    ("09_en_render", "setup", [(1240.3, 1409.6), (1710.0, 1400.1), (1724.8, 2133.4), (1255.1, 2142.9)], True, []),
    ("zero_en_render", "all", rect(1856.6, 993.6, 2229.6, 1580.6), True,
     [("mirror", over(x0=2196.9, shadow=4), None)]),                        # Lucky 13 on its right
    ("lucky13_en_render", "all", rect(2196.9, 945.5, 2569.9, 1532.5), True,
     [("mirror", over(x0=2537.5), None)]),                                  # Unlucky 7 on its right
    ("unlucky7_en_render", "all", rect(2537.5, 990.5, 2910.5, 1577.5), True, []),
    ("div2_en_render", "all", rect(1057.5, 2295.5, 1430.5, 2882.5), True, []),
    ("minus2_en_render", "all", rect(702.5, 2255.5, 1075.5, 2842.5), True,
     [("mirror", over(x0=1057.5), None)]),                                  # ÷2 on its right
    ("minus4_en_render", "all", rect(384.6, 2199.5, 757.6, 2786.5), True,
     [("mirror", over(x0=702.5), None)]),                                   # -2 on its right
    ("minus6_en_render", "all", rect(1003.2, 1818.2, 1376.2, 2405.2), True,
     [("template", over(x0=1057.5, y0=2295.5), "div2_en_render"),           # ÷2 on its bottom right
      ("template", over(y0=2255.5, x1=1075.5), "div2_en_render")]),          # -2 on its bottom left
    ("minus8_en_render", "setup", [(2355.1, 598.2), (2823.8, 604.7), (2813.6, 1339.3), (2344.9, 1332.8)], True, []),
    ("minus10_en_render", "all", rect(301.6, 1737.5, 674.6, 2324.5), True,
     [("template", over(x0=384.6, y0=2199.5), "div2_en_render"),            # -4 on its bottom
      ("mirror", over(x0=656.5), None)]),                                   # -8 on its right
    ("discard_en_render", "all", rect(2183.5, 2290.5, 2556.5, 2877.5), True, []),
    ("steal_en_render", "setup", [(1276.9, 620.0), (1746.0, 625.7), (1737.1, 1360.8), (1268.0, 1355.1)], True, []),
    ("swap_en_render", "all", rect(2325.5, 1842.6, 2698.5, 2429.6), True,
     [("register", over(y0=2290.5, x1=2556.5), ("photo", (0, 1150, 520, 1960)))]),   # Discard on it
    ("just_one_more_en_render", "all", rect(1648.5, 1704.3, 2021.5, 2291.3), True,
     [("register", over(x0=1979.6, y0=1759.3), ("bgg", (517, 50, 884, 633))),        # Flip Four
      ("register", over(x0=1834.4, y0=2240.5), ("bgg", (517, 50, 884, 633)))]),      # Steal
    ("flip4_en_render", "all", rect(1979.6, 1759.3, 2352.6, 2346.3), True,
     [("register", over(x0=2325.5, y0=1842.6), ("bgg", (1451, 50, 1818, 633))),      # Swap
      ("register", over(y0=2240.5), ("bgg", (1451, 50, 1818, 633)))]),               # Steal, Discard
]
# Scans (BGG crop box in source px) aligned onto the render cutout of the same card.
SCANS = [
    ("just_one_more_en_scan", (517, 50, 884, 633), "just_one_more_en_render"),
    ("flip4_en_scan", (1451, 50, 1818, 633), "flip4_en_render"),
    ("steal_en_scan", (517, 733, 884, 1316), "steal_en_render"),
    ("discard_en_scan", (984, 733, 1351, 1316), "discard_en_render"),
]
RENDER_RADIUS = 22.0   # source px, render corner radius (measured ~17) plus a safety margin


def quad_h(quad, w, h):
    return cv2.getPerspectiveTransform(np.float32(quad), np.float32([[0, 0], [w, 0], [w, h], [0, h]]))


def cover_mask(cover, m, shape_src, w, h):
    hs, ws = shape_src
    src = np.zeros((hs, ws), np.uint8)
    x0, y0, x1, y1 = (max(0, int(np.floor(cover[0]))), max(0, int(np.floor(cover[1]))),
                      min(ws, int(np.ceil(cover[2]))), min(hs, int(np.ceil(cover[3]))))
    src[y0:y1, x0:x1] = 255
    out = cv2.warpPerspective(src, m, (w, h), flags=cv2.INTER_NEAREST) > 0
    return cv2.dilate(out.astype(np.uint8), np.ones((3, 3), np.uint8)) > 0


def sift_homography(src, dst, dst_mask, src_box):
    """Homography src (full image px) -> dst (cutout px) from SIFT matches inside dst_mask / src_box."""
    sift = cv2.SIFT_create(nfeatures=6000)
    bx0, by0, bx1, by1 = src_box
    smask = np.zeros(src.shape[:2], np.uint8)
    smask[by0:by1, bx0:bx1] = 255
    g1 = cv2.cvtColor(src, cv2.COLOR_BGR2GRAY)
    g2 = cv2.cvtColor(dst, cv2.COLOR_BGR2GRAY)
    k1, d1 = sift.detectAndCompute(g1, smask)
    k2, d2 = sift.detectAndCompute(g2, dst_mask.astype(np.uint8) * 255)
    matches = cv2.BFMatcher().knnMatch(d1, d2, k=2)
    good = [a for a, b in (p for p in matches if len(p) == 2) if a.distance < 0.75 * b.distance]
    best = {}  # one match per dst keypoint (else RANSAC can collapse onto a degenerate homography)
    for g in good:
        if g.trainIdx not in best or g.distance < best[g.trainIdx].distance:
            best[g.trainIdx] = g
    good = list(best.values())
    p1 = np.float32([k1[g.queryIdx].pt for g in good])
    p2 = np.float32([k2[g.trainIdx].pt for g in good])
    hm, inl = cv2.findHomography(p1, p2, cv2.RANSAC, 3.0)
    if hm is None:
        raise SystemExit("homography failed")
    # sanity: the cutout must map back to a convex quad that roughly fills the source box
    hd, wd = dst.shape[:2]
    q = cv2.perspectiveTransform(np.float32([[[0, 0], [wd, 0], [wd, hd], [0, hd]]]), np.linalg.inv(hm))[0]
    mx, my = 0.15 * (bx1 - bx0), 0.15 * (by1 - by0)
    inside = (q[:, 0].min() > bx0 - mx and q[:, 0].max() < bx1 + mx and q[:, 1].min() > by0 - my
              and q[:, 1].max() < by1 + my)
    if not cv2.isContourConvex(q.astype(np.float32)) or not inside or cv2.contourArea(q) < 0.25 * (bx1 - bx0) * (by1 - by0):
        raise SystemExit(f"implausible homography: cutout corners -> {np.round(q, 1).tolist()} (box {src_box})")
    n = int(inl.sum())
    err = np.linalg.norm(cv2.perspectiveTransform(p1[inl.ravel() > 0][None], hm)[0] - p2[inl.ravel() > 0], axis=1)
    return hm, n, len(good), float(np.median(err))


def colour_fit(src, dst, mask):
    """Colour map src -> dst fitted on mask pixels: per output channel a quadratic polynomial in the
    three input channels (handles the scan's whiter banners / different ground vs the render)."""
    def feats(a):
        x = a.reshape(-1, 3).astype(np.float32) / 255
        r, g, b = x[:, 2], x[:, 1], x[:, 0]
        return np.stack([r, g, b, r * r, g * g, b * b, r * g, r * b, g * b, np.ones_like(r)], 1)
    fs = feats(src)
    sel = mask.reshape(-1)
    coef, *_ = np.linalg.lstsq(fs[sel], dst.reshape(-1, 3)[sel].astype(np.float32), rcond=None)
    return np.clip(fs @ coef, 0, 255).reshape(src.shape).astype(np.uint8)


def offset_field(fill, ref, vis, cov, sigma=25.0):
    """Remove the remaining low-frequency colour difference between fill and ref: the difference on
    the visible pixels next to the covered area, smoothly extrapolated into it (normalized convolution)."""
    near = cv2.dilate(cov.astype(np.uint8), np.ones((41, 41), np.uint8)).astype(bool) & vis
    diff = (ref.astype(np.float32) - fill.astype(np.float32)) * near[..., None]
    wsum = cv2.GaussianBlur(near.astype(np.float32), (0, 0), sigma)
    dsum = cv2.GaussianBlur(diff, (0, 0), sigma)
    field = dsum / np.maximum(wsum, 1e-3)[..., None]
    field *= np.clip(wsum / 0.02, 0, 1)[..., None]  # fade out far from any visible support
    return np.clip(fill.astype(np.float32) + field, 0, 255).astype(np.uint8)


def feather_paste(rgb, fill, mask, r=4):
    """Paste fill into rgb on mask with a soft edge that only extends into mask."""
    soft = cv2.GaussianBlur(mask.astype(np.float32), (0, 0), r)
    soft = np.where(mask, np.clip(soft * 2, 0, 1), 0)[..., None]
    soft = np.maximum(soft, cv2.erode(mask.astype(np.uint8), np.ones((2 * r + 1, 2 * r + 1), np.uint8))[..., None])
    return (rgb * (1 - soft) + fill * soft).astype(np.uint8)


def corner_holes(w, h, rx, ry):
    """Mask of the area outside a rounded rect with elliptical corner radii rx, ry (cutout px)."""
    sil = np.zeros((h, w), np.uint8)
    rxi, ryi = int(round(rx)), int(round(ry))
    cv2.rectangle(sil, (rxi, 0), (w - 1 - rxi, h - 1), 255, cv2.FILLED)
    cv2.rectangle(sil, (0, ryi), (w - 1, h - 1 - ryi), 255, cv2.FILLED)
    for cx, cy in [(rxi, ryi), (w - 1 - rxi, ryi), (w - 1 - rxi, h - 1 - ryi), (rxi, h - 1 - ryi)]:
        cv2.ellipse(sil, (cx, cy), (rxi, ryi), 0, 0, 360, 255, cv2.FILLED)
    return sil == 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sheet", action="store_true", help="also write contact_sheet.jpg into the output dir")
    args = ap.parse_args()

    geo = load_config(CONFIG)["geometry"]
    w = int(geo["cutout_width"])
    h = int(geo["cutout_height"]) or int(round(w / geo["aspect"]))
    alpha = rounded_alpha(w, h, geo["corner_radius"] * w)
    imgs = {k: cv2.imread(str(p), cv2.IMREAD_COLOR) for k, p in SRC.items()}
    for k, v in imgs.items():
        if v is None:
            raise SystemExit(f"cannot read {SRC[k]}")
    OUT.mkdir(parents=True, exist_ok=True)
    done: dict[str, np.ndarray] = {}

    for name, src, quad, render, fills in CARDS:
        img = imgs[src]
        m = quad_h(quad, w, h)
        rgb = cv2.warpPerspective(img, m, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)
        covers = [cover_mask(c, m, img.shape[:2], w, h) for _, c, _ in fills]
        visible = ~np.any(covers, axis=0) if covers else np.ones((h, w), bool)
        notes = []
        hcache = {}
        for (fill, _, extra), cov in zip(fills, covers):
            if fill == "mirror":
                rgb = np.where(cov[..., None], rgb[:, ::-1], rgb)
            elif fill == "template":
                rgb = feather_paste(rgb, done[extra], cov, r=2)
            elif fill == "register":
                key, box = extra
                vis = visible & ~corner_holes(w, h, 30, 30)
                if (key, box) not in hcache:
                    hcache[(key, box)] = sift_homography(imgs[key], rgb, vis, box)
                hm, n, ng, err = hcache[(key, box)]
                warped = cv2.warpPerspective(imgs[key], hm, (w, h), flags=cv2.INTER_CUBIC,
                                             borderMode=cv2.BORDER_REPLICATE)
                band = cv2.dilate(cov.astype(np.uint8), np.ones((61, 61), np.uint8)).astype(bool) & vis
                warped = colour_fit(warped, rgb, band if band.sum() > 2000 else vis)
                warped = offset_field(warped, rgb, vis, cov)
                rgb = feather_paste(rgb, warped, cov, r=3)
                notes.append(f"register {key}: {n}/{ng} inliers, median err {err:.2f} px")
            if fill == "mirror":
                seam = cv2.dilate(cov.astype(np.uint8), np.ones((5, 5), np.uint8)) - cov.astype(np.uint8)
                if seam.any():
                    blur = cv2.GaussianBlur(rgb, (0, 0), 1.0)
                    rgb = np.where(seam[..., None] > 0, blur, rgb)
        if render:
            # source px -> cutout px scale of this card (sides of the quad)
            q = np.float32(quad)
            sx = w / np.linalg.norm(q[1] - q[0])
            sy = h / np.linalg.norm(q[3] - q[0])
            hole = corner_holes(w, h, RENDER_RADIUS * sx, RENDER_RADIUS * sy).astype(np.uint8) * 255
            hole = cv2.dilate(hole, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7)))
            rgb = cv2.inpaint(rgb, hole, 7, cv2.INPAINT_TELEA)
        done[name] = rgb
        cv2.imwrite(str(OUT / f"{name}.png"), np.dstack([rgb, alpha]))
        covered = int(np.any(covers, axis=0).sum()) if covers else 0
        print(f"{name:24s} {w}x{h} from {src:5s} covered {covered / (w * h):5.1%}  " + "; ".join(notes))

    for name, box, ref in SCANS:
        hm, n, ng, err = sift_homography(imgs["bgg"], done[ref], ~corner_holes(w, h, 30, 30), box)
        x0, y0, x1, y1 = box
        crop = imgs["bgg"][y0:y1, x0:x1]  # warped with BORDER_REPLICATE: the scan is cut a bit inside the card edge
        shift = np.array([[1, 0, x0], [0, 1, y0], [0, 0, 1]], np.float64)
        rgb = cv2.warpPerspective(crop, hm @ shift, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)
        cv2.imwrite(str(OUT / f"{name}.png"), np.dstack([rgb, alpha]))
        cmap = np.linalg.inv(hm @ shift)
        corners = cv2.perspectiveTransform(np.float32([[[0, 0], [w, 0], [w, h], [0, h]]]), cmap)[0]
        print(f"{name:24s} {w}x{h} from bgg   aligned to {ref}: {n}/{ng} inliers, median err {err:.2f} px; "
              f"card corners in crop px {np.round(corners, 1).tolist()}")

    if args.sheet:
        names = [c[0] for c in CARDS] + [s[0] for s in SCANS]
        tw, th, cols = 240, int(round(240 * h / w)), 6
        rows = (len(names) + cols - 1) // cols
        sheet = np.full((rows * (th + 26) + 8, cols * (tw + 8) + 8, 3), 64, np.uint8)
        for i, n in enumerate(names):
            im = cv2.imread(str(OUT / f"{n}.png"), cv2.IMREAD_UNCHANGED)
            im = cv2.resize(im, (tw, th), interpolation=cv2.INTER_AREA)
            a = im[..., 3:4].astype(np.float32) / 255
            r, c = divmod(i, cols)
            x, y = 8 + c * (tw + 8), 8 + r * (th + 26)
            sheet[y:y + th, x:x + tw] = (im[..., :3] * a + sheet[y:y + th, x:x + tw] * (1 - a)).astype(np.uint8)
            cv2.putText(sheet, n, (x, y + th + 18), cv2.FONT_HERSHEY_SIMPLEX, 0.42, (255, 255, 255), 1, cv2.LINE_AA)
        cv2.imwrite(str(OUT / "contact_sheet.jpg"), sheet, [cv2.IMWRITE_JPEG_QUALITY, 90])
        print(f"contact sheet -> {OUT / 'contact_sheet.jpg'}")


if __name__ == "__main__":
    main()
