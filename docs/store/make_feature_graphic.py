"""Google Play feature graphic (1024 x 500, 24-bit PNG without alpha), English and German.

Brand only: ground #0A1220, the Scan7 icon, the wordmark "scan" + "7" (Unbounded Bold from the
app's res/font), a two-line tagline (DM Mono), and a stylised phone scanning plain cards with the
brand scan boxes (cyan, white outline). No Flip 7 artwork, logo, fonts or photos. Important
content stays inside the central 924 x 400 safe area. Spec: docs/store/README.md.

Usage (from the repo root):
    uv run --project ml python -I docs/store/make_feature_graphic.py
    uv run --project ml python -I docs/store/make_feature_graphic.py --lang de --out /tmp/fg.png
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / "app/app/src/main/res/font"
ICON = ROOT / "brand/scan7_icon_1024.png"
OUT_DIR = ROOT / "docs/store"

W, H = 1024, 500
SS = 3  # supersampling factor (drawn at 3x, downscaled for smooth edges)

GROUND = (10, 18, 32)
SURFACE = (21, 34, 58)
SURFACE_HI = (33, 50, 82)
TEXT = (238, 244, 250)
MUTED = (147, 164, 188)
ACCENT = (56, 214, 245)
WHITE = (255, 255, 255)

TAGLINES = {
    "en": ("Scan your cards.", "Keep score."),
    "de": ("Karten scannen.", "Punkte zählen."),
}
SCORE_LABEL = {"en": "points", "de": "Punkte"}


def s(v: float) -> int:
    return round(v * SS)


def font(name: str, size: float) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), s(size))


def scan_box(d: ImageDraw.ImageDraw, box: tuple[float, float, float, float], r: float = 8) -> None:
    """Brand scan box: cyan stroke with a thin white outline on both sides (drawScanBox)."""
    x0, y0, x1, y1 = box
    d.rounded_rectangle([s(x0 - 1.5), s(y0 - 1.5), s(x1 + 1.5), s(y1 + 1.5)], radius=s(r + 1.5),
                        outline=WHITE, width=s(6))
    d.rounded_rectangle([s(x0), s(y0), s(x1), s(y1)], radius=s(r), outline=ACCENT, width=s(3))


def phone_layer(lang: str) -> Image.Image:
    """The stylised phone (upright, transparent around it), later rotated onto the canvas."""
    pw, ph = 210, 384
    img = Image.new("RGBA", (s(pw), s(ph)), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Body + screen.
    d.rounded_rectangle([0, 0, s(pw) - 1, s(ph) - 1], radius=s(30), fill=SURFACE_HI)
    d.rounded_rectangle([s(7), s(7), s(pw - 7), s(ph - 7)], radius=s(24), fill=GROUND)
    d.ellipse([s(pw / 2 - 5), s(16), s(pw / 2 + 5), s(26)], fill=SURFACE_HI)  # camera dot

    # Plain cards (no game art): light rectangles with a digit, each in a scan box.
    cards = [("3", 22, 72, -4), ("8", 82, 64, 3), ("12", 142, 74, -2)]
    mono = font("dm_mono_medium.ttf", 20)
    for label, x, y, angle in cards:
        cw, ch = 46, 66
        card = Image.new("RGBA", (s(cw + 16), s(ch + 16)), (0, 0, 0, 0))
        cd = ImageDraw.Draw(card)
        cd.rounded_rectangle([s(8), s(8), s(8 + cw), s(8 + ch)], radius=s(7), fill=(226, 234, 244))
        cd.text((s(8 + cw / 2), s(8 + ch / 2)), label, font=mono, fill=SURFACE, anchor="mm")
        scan_box(cd, (5, 5, 11 + cw, 11 + ch), r=9)
        card = card.rotate(angle, resample=Image.BICUBIC, expand=True)
        img.alpha_composite(card, (s(x - 8), s(y - 8)))

    # Score panel.
    d.rounded_rectangle([s(22), s(182), s(pw - 22), s(286)], radius=s(16), fill=SURFACE)
    d.text((s(pw / 2), s(226)), "23", font=font("unbounded_bold.ttf", 46), fill=ACCENT, anchor="mm")
    d.text((s(pw / 2), s(267)), SCORE_LABEL[lang], font=font("dm_mono_regular.ttf", 15), fill=MUTED,
           anchor="mm")
    # Shutter button.
    d.ellipse([s(pw / 2 - 26), s(310), s(pw / 2 + 26), s(362)], outline=TEXT, width=s(4))
    d.ellipse([s(pw / 2 - 18), s(318), s(pw / 2 + 18), s(354)], fill=ACCENT)
    return img


def render(lang: str) -> Image.Image:
    img = Image.new("RGBA", (s(W), s(H)), GROUND + (255,))
    d = ImageDraw.Draw(img)

    # Soft surface panel behind the phone side.
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([s(600), s(20), s(1010), s(480)], fill=SURFACE + (255,))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(s(40))))

    # Icon + wordmark.
    icon = Image.open(ICON).convert("RGBA").resize((s(112), s(112)), Image.LANCZOS)
    img.alpha_composite(icon, (s(84), s(98)))
    wm = font("unbounded_bold.ttf", 86)
    x, base = s(222), s(184)
    d.text((x, base), "scan", font=wm, fill=TEXT, anchor="ls")
    d.text((x + d.textlength("scan", font=wm), base), "7", font=wm, fill=ACCENT, anchor="ls")

    # Tagline.
    tag = font("dm_mono_medium.ttf", 38)
    l1, l2 = TAGLINES[lang]
    d.text((s(86), s(300)), l1, font=tag, fill=TEXT, anchor="ls")
    d.text((s(86), s(352)), l2, font=tag, fill=ACCENT, anchor="ls")
    d.rounded_rectangle([s(86), s(246), s(150), s(252)], radius=s(3), fill=ACCENT)

    # Phone, slightly tilted, with a soft shadow.
    phone = phone_layer(lang).rotate(-7, resample=Image.BICUBIC, expand=True)
    px, py = s(800) - phone.width // 2, s(250) - phone.height // 2
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 150), (px + s(10), py + s(14)), phone.getchannel("A"))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(s(14))))
    img.alpha_composite(phone, (px, py))

    return img.resize((W, H), Image.LANCZOS).convert("RGB")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--lang", choices=sorted(TAGLINES), action="append",
                    help="language(s) to render (default: all)")
    ap.add_argument("--out", type=Path, help="output path (only with a single --lang)")
    args = ap.parse_args()
    langs = args.lang or sorted(TAGLINES)
    if args.out and len(langs) != 1:
        ap.error("--out needs exactly one --lang")
    for lang in langs:
        out = args.out or OUT_DIR / f"feature-graphic-{lang}.png"
        render(lang).save(out, optimize=True)
        print(out)


if __name__ == "__main__":
    main()
