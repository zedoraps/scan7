"""One-off: cut the English-edition variant looks out of the deck render and store them as
rectified RGBA sources in ml/data/cards_extra/ (make_cutouts.py copies them into out/cutouts).

    uv run python scripts/extract_en_variants.py

Source: ml/data/web_test/images/paste_773e114e.jpg (2250x3000 render of the whole English deck
on white; excluded from evaluation, see data/web_test/exclude.txt). The cards are axis-aligned.
The card rectangles below were measured by hand on that image (saturation profiles). The render
is squashed horizontally (cards ~0.64 w/h instead of 0.721), so rectifying to 600x832 restores
the real aspect.

Per card, pixels that are not the card's own print are replaced:
  * strips covered by a neighbouring card (00: card "1" lies on its right edge; back: the "0"
    lies on its right edge, both incl. the neighbour's soft drop shadow) -> for 00 mirrored from the (symmetric) left edge, for the back
    extended from the last visible column;
  * the render's corners are rounder than the real card (radius ~0.105 w vs 0.042 w), so the
    background inside the cards.yaml rounded alpha but outside the render silhouette is inpainted.
Then the same rounded-corner alpha as make_cutouts.py (corner radius from cards.yaml) is applied.
"""

from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

from make_cutouts import load_config, rounded_alpha

ML = Path(__file__).resolve().parents[1]
SRC = ML / "data" / "web_test" / "images" / "paste_773e114e.jpg"
OUT = ML / "data" / "cards_extra"

# name: (card rect [x0, y0, x1, y1] in source px, covered region [x0, y0, x1, y1] in source px, fill)
CARDS = {
    "00_en_rainbow": ((405.0, 679.5, 697.5, 1136.5), (681.0, 0, 9999, 1114.0), "mirror"),
    "back_en": ((116.0, 642.0, 408.0, 1101.0), (395.0, 670.0, 9999, 9999), "extend"),
    # lies on top of Freeze, nothing covered (empty cover rect)
    "second_chance_en": ((1787.0, 2147.5, 2078.0, 2606.0), (0, 0, 0, 0), "mirror"),
}
EXTEND_SLOPE = 0.31  # back: banner edges rise ~0.31 px per px to the right (cutout px)
RENDER_RADIUS = 40.0  # source px, render corner radius (measured ~31) plus a safety margin


def rect_to_cutout(img, rect, w, h, interp=cv2.INTER_CUBIC):
    x0, y0, x1, y1 = rect
    src = np.float32([[x0, y0], [x1, y0], [x1, y1], [x0, y1]])
    dst = np.float32([[0, 0], [w, 0], [w, h], [0, h]])
    m = cv2.getPerspectiveTransform(src, dst)
    return cv2.warpPerspective(img, m, (w, h), flags=interp, borderMode=cv2.BORDER_REPLICATE)


def main():
    cfg = load_config()
    geo = cfg["geometry"]
    w = int(geo["cutout_width"])
    h = int(geo["cutout_height"]) or int(round(w / geo["aspect"]))
    alpha = rounded_alpha(w, h, geo["corner_radius"] * w)
    img = cv2.imread(str(SRC), cv2.IMREAD_COLOR)
    if img is None:
        raise SystemExit(f"cannot read {SRC}")
    OUT.mkdir(parents=True, exist_ok=True)

    for name, (rect, cover, fill) in CARDS.items():
        # masks in source coordinates: covered by a neighbour / outside the render silhouette
        hs, ws = img.shape[:2]
        covered = np.zeros((hs, ws), np.uint8)
        cx0, cy0, cx1, cy1 = cover
        covered[int(np.floor(cy0)):int(np.ceil(cy1)), int(np.floor(cx0)):int(np.ceil(cx1))] = 255
        x0, y0, x1, y1 = rect
        r = RENDER_RADIUS
        sil = np.zeros((hs, ws), np.uint8)  # render silhouette (rounded rect)
        pts = lambda *p: tuple(int(round(v)) for v in p)
        cv2.rectangle(sil, pts(x0 + r, y0), pts(x1 - r, y1), 255, cv2.FILLED)
        cv2.rectangle(sil, pts(x0, y0 + r), pts(x1, y1 - r), 255, cv2.FILLED)
        for cx, cy in [(x0 + r, y0 + r), (x1 - r, y0 + r), (x1 - r, y1 - r), (x0 + r, y1 - r)]:
            cv2.circle(sil, pts(cx, cy), int(round(r)), 255, cv2.FILLED)

        rgb = rect_to_cutout(img, rect, w, h)
        cov = rect_to_cutout(covered, rect, w, h, cv2.INTER_NEAREST) > 0
        out_sil = rect_to_cutout(sil, rect, w, h, cv2.INTER_NEAREST) == 0
        cov = cv2.dilate(cov.astype(np.uint8), np.ones((3, 3), np.uint8)) > 0

        if fill == "mirror":
            mir = rgb[:, ::-1]
            rgb = np.where(cov[..., None], mir, rgb)
        else:  # extend the last visible column along the FLIP7 banner's slope
            c0 = int(np.where(cov.any(axis=0))[0].min())
            src = rgb.copy()
            ys, xs = np.nonzero(cov)
            sy = np.clip(np.round(ys + EXTEND_SLOPE * (xs - c0 + 1)).astype(int), 0, h - 1)
            rgb[ys, xs] = src[sy, c0 - 1]
        # soften the seam
        seam = cv2.dilate(cov.astype(np.uint8), np.ones((5, 5), np.uint8)) - cov.astype(np.uint8)
        if seam.any():
            blur = cv2.GaussianBlur(rgb, (0, 0), 1.0)
            rgb = np.where(seam[..., None] > 0, blur, rgb)

        # background showing in the render's rounder corners (inside our alpha only)
        # (the whole exterior is masked so inpainting only draws from the card itself)
        hole = out_sil.astype(np.uint8) * 255
        hole = cv2.dilate(hole, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
        rgb = cv2.inpaint(rgb, hole, 9, cv2.INPAINT_TELEA)

        rgba = np.dstack([rgb, alpha])
        cv2.imwrite(str(OUT / f"{name}.png"), rgba)
        print(f"{name}: {w}x{h}, covered {int(cov.sum())} px filled ({fill}), corners inpainted {int(((hole > 0) & (alpha > 0)).sum())} px")


if __name__ == "__main__":
    main()
