"""Caption band for the Play screenshots (post-processing of scripts/store-screenshots.sh output).

Reads docs/store/screenshots/<locale>/<n>_<screen>.png and writes
docs/store/screenshots/<locale>/captioned/<n>_<screen>.png: the same 1080 x 1920 size, a caption
band on top (Unbounded SemiBold, brand ground) and the screenshot scaled down below it in a
rounded frame. Caption strings = the shot list in docs/store/README.md (keep them in sync).

Usage (from the repo root):
    uv run --project ml python -I docs/store/caption_screenshots.py
    uv run --project ml python -I docs/store/caption_screenshots.py --locale de-DE
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / "app/app/src/main/res/font"
SHOTS = ROOT / "docs/store/screenshots"

W, H = 1080, 1920
BAND = 330          # caption band height
MARGIN = 60         # space below the framed screenshot
GROUND = (10, 18, 32)
SURFACE_HI = (33, 50, 82)
TEXT = (238, 244, 250)

# screen name (file stem after "<n>_") -> caption per locale, from docs/store/README.md.
CAPTIONS = {
    "round_table": {"en-US": "Score a whole round in a minute", "de-DE": "Eine ganze Runde in einer Minute"},
    "review": {"en-US": "Scan the cards, get the points", "de-DE": "Karten scannen, Punkte sehen"},
    "round_list": {"en-US": "Everyone's round at a glance", "de-DE": "Die Runde aller auf einen Blick"},
    "sheet_open": {"en-US": "Fix a card or enter it by hand", "de-DE": "Karte korrigieren oder von Hand eingeben"},
    "scoreboard": {"en-US": "Every round on the scoreboard", "de-DE": "Alle Runden in der Punktetabelle"},
    "gameover": {"en-US": "Who won tonight", "de-DE": "Wer heute gewonnen hat"},
    "home": {"en-US": "Your past games", "de-DE": "Deine vergangenen Spiele"},
    "newgame": {"en-US": "Your regular players, ready to go", "de-DE": "Deine Mitspieler, sofort bereit"},
}


def wrap(d: ImageDraw.ImageDraw, text: str, fnt: ImageFont.FreeTypeFont, width: int) -> list[str]:
    lines, cur = [], ""
    for word in text.split():
        trial = f"{cur} {word}".strip()
        if cur and d.textlength(trial, font=fnt) > width:
            lines.append(cur)
            cur = word
        else:
            cur = trial
    return lines + [cur]


def caption(shot: Path, text: str) -> Image.Image:
    img = Image.new("RGB", (W, H), GROUND)
    d = ImageDraw.Draw(img)
    fnt = ImageFont.truetype(str(FONTS / "unbounded_semibold.ttf"), 64)
    lines = wrap(d, text, fnt, W - 160)
    lh = 84
    y = BAND / 2 - lh * (len(lines) - 1) / 2 + 10
    for i, line in enumerate(lines):
        d.text((W / 2, y + i * lh), line, font=fnt, fill=TEXT, anchor="mm")

    src = Image.open(shot).convert("RGB")
    h = H - BAND - MARGIN
    w = round(src.width * h / src.height)
    src = src.resize((w, h), Image.LANCZOS)
    x = (W - w) // 2
    frame = 10
    d.rounded_rectangle([x - frame, BAND - frame, x + w + frame, BAND + h + frame], radius=44, fill=SURFACE_HI)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=36, fill=255)
    img.paste(src, (x, BAND), mask)
    return img


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--locale", action="append", help="locale folder(s), default: en-US de-DE")
    ap.add_argument("--shots", type=Path, default=SHOTS, help="screenshot root (default: %(default)s)")
    args = ap.parse_args()
    for locale in args.locale or ["en-US", "de-DE"]:
        out_dir = args.shots / locale / "captioned"
        out_dir.mkdir(parents=True, exist_ok=True)
        for shot in sorted((args.shots / locale).glob("*_*.png")):
            screen = shot.stem.split("_", 1)[1]
            text = CAPTIONS.get(screen, {}).get(locale)
            if text is None:
                print(f"skip {shot.name}: no {locale} caption for '{screen}'")
                continue
            caption(shot, text).save(out_dir / shot.name, optimize=True)
            print(out_dir / shot.name)


if __name__ == "__main__":
    main()
