"""Contact sheets for the app screenshot matrix (scripts/screen-matrix.sh).

One sheet per screen: that screen on every profile side by side, scaled to the same height,
labelled with the profile (WxH@density x font_scale). Missing shots are skipped.

Usage (from ml/):
    uv run python -I scripts/contact_sheets.py                      # ../out/screens
    uv run python -I scripts/contact_sheets.py --screens ../out/screens --height 900
"""

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


def load_font(size: int) -> ImageFont.ImageFont:
    for name in ("/System/Library/Fonts/Menlo.ttc", "DejaVuSansMono.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--screens", type=Path, default=Path(__file__).resolve().parents[2] / "out" / "screens")
    ap.add_argument("--profiles", nargs="*", help="profile dirs in this order (default: all, sorted)")
    ap.add_argument("--height", type=int, default=800, help="height of each shot on the sheet (px)")
    ap.add_argument("--gap", type=int, default=16)
    ap.add_argument("--per-row", type=int, default=7, help="shots per row")
    args = ap.parse_args()

    root: Path = args.screens
    profiles = args.profiles or sorted(p.name for p in root.iterdir() if p.is_dir())
    profiles = [p for p in profiles if (root / p).is_dir()]
    screens = sorted({f.stem for p in profiles for f in (root / p).glob("*.png")})
    font = load_font(22)
    label_h = 60
    for screen in screens:
        tiles = []
        for p in profiles:
            f = root / p / f"{screen}.png"
            if not f.exists():
                continue
            try:
                img = Image.open(f).convert("RGB")
            except OSError:
                continue
            w = round(img.width * args.height / img.height)
            tiles.append((p, img.resize((w, args.height), Image.LANCZOS)))
        if not tiles:
            continue
        rows = [tiles[i : i + args.per_row] for i in range(0, len(tiles), args.per_row)]
        row_h = args.height + label_h + args.gap
        width = max(sum(t.width for _, t in r) + args.gap * (len(r) + 1) for r in rows)
        sheet = Image.new("RGB", (width, row_h * len(rows) + args.gap), (40, 40, 40))
        draw = ImageDraw.Draw(sheet)
        for ri, row in enumerate(rows):
            x, y = args.gap, ri * row_h + args.gap
            for p, t in row:
                size, _, rest = p.partition("@")
                density, _, scale = rest.partition("x")
                draw.text((x, y), size, fill=(255, 255, 255), font=font)
                draw.text((x, y + 28), f"{density}dpi  font {scale}", fill=(255, 210, 90), font=font)
                sheet.paste(t, (x, y + label_h))
                x += t.width + args.gap
        out = root / f"sheet_{screen}.jpg"
        sheet.save(out, quality=85)
        print(out)


if __name__ == "__main__":
    main()
