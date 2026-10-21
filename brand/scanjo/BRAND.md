# Scanjo brand

Scanjo is a score counter for the card game Skyjo, by **Bastelapps**. It is a sibling of Scan7 and shares that app's layout and flow, but has its own icon, colors and fonts.

The source of truth is this kit. `design/reference/Main.dc.html` is the brand guide page, kept for reference only. Don't parse it for values: use `tokens.json`, `android/res/values*/colors.xml` or `compose/ScanjoTheme.kt`.

## Icon

A 3 × 4 grid of cards (one player's hand) has these parts:
- The **third column is dashed**: a cleared column.
- **Two small minus marks** show that low scores win.
- A **scan line runs through the middle of the last row**.
- The whole mark is **tilted −8°** and **centred in the tile**.

| Part | Color |
|---|---|
| Tile | `#2A6FD8` |
| Cards and dashes | `#FFFFFF` |
| Minus marks | tile color, cut out of the cards |
| Scan line | `#12233D` with a 2-unit tile-colored outline |

Geometry (100 × 100 units, before the transform `translate(0 8.5) rotate(-8 50 50)`):
- Cards: 13 × 17, radius 3.
- Columns start at x = 18 / 35 / 52 / 69 and rows at y = 12 / 33 / 54.
- Scan line: x 10–90, y 59.5–65.5, radius 3.

The icon is the same in light and dark mode. Never recolor, stretch or outline it.

| File | Use |
|---|---|
| `icon/scanjo_icon.svg` | Master, rounded tile |
| `icon/scanjo_icon_square.svg`, `scanjo_playstore_512.png` | Play Store icon (Play rounds the corners itself) |
| `icon/scanjo_icon_round.svg` | Round crop |
| `icon/scanjo_mark_on_dark.svg` | Mark without a tile |
| `icon/scanjo_mark_mono.svg` / `_white.svg` | One-color mark, with the details cut out |
| `store/feature_graphic_1024x500.png` | Play Store feature graphic |
| `android/res/…` | Adaptive launcher icon: foreground, themed monochrome, background color, plus legacy PNGs |

The adaptive foreground is scaled to 0.66 so the whole mark stays inside the 66 dp safe circle.

## Wordmark

- Always lowercase: **scanjo**. Fredoka SemiBold.
- "scan" is in the text color and "jo" in the accent color.
- Clear space around the icon + wordmark lockup: ¼ of the icon width. The icon's minimum size is 24 px.

## Color

Two themes:
- **Sky Day** is the default.
- **Sky Night** is dark mode, following the system setting.

Android `values/` holds Sky Day and `values-night/` holds Sky Night. Both use the same names (`sj_*`).

| Role | Sky Day | Sky Night | Use |
|---|---|---|---|
| ground | `#EAF3FB` | `#10172E` | App background |
| surface | `#FFFFFF` | `#1B2443` | Open player cards, sheets |
| line | `#CFE0F0` | `#2E3B66` | Borders, dividers |
| text | `#12233D` | `#EEF3FF` | Primary text |
| muted | `#4F6580` | `#A0AECB` | Labels, secondary text |
| accent | `#2A6FD8` | `#6FA0FF` | Primary buttons, highlights |
| accentText | `#2563C7` | `#8DB4FF` | Accent-colored text on tint |
| accentTint | `#DCE9FB` | `#1F2F57` | Scored tags, selection |
| onAccent | `#FFFFFF` | `#10172E` | Text on accent buttons |
| penalty | `#D62E7E` | `#FF5FA2` | ×2 penalty only |
| doneBg / doneLine / doneInk | `#E1ECF6` / `#D6E4F1` / `#6B7F98` | `#131B35` / `#1B2443` / `#8190B0` | Finished players |
| openDash | `#9FB6CF` | `#3C4A78` | Dashed "OPEN" tag border |

**Player colors** are the same 15 as Scan7's avatar palette (`sj_player_*`, `PlayerColors`). Pink is never a player color.

**Camera overlay:**
- A detected card gets a 3 dp `#2A6FD8` box with a 2 dp **white outline outside it**. Skyjo's −2/−1/0 cards are blue, so blue alone would disappear.
- Slots of a cleared column get a dashed white outline.

## Type

| Style | Font | Size |
|---|---|---|
| score | Fredoka SemiBold | 64 |
| title | Fredoka SemiBold | 32 |
| heading | Fredoka SemiBold | 22 |
| rowTotal | Fredoka SemiBold | 26 |
| button | Fredoka SemiBold | 17 |
| body | DM Mono Regular | 15 / 24 |
| tag | DM Mono Medium | 12 |
| label | DM Mono Regular | 12, caps, +0.2 em |

- Write negative scores with a real minus sign `−` (U+2212), never a hyphen.
- The bundled fonts are the Latin subset. They have `−` and `×` but **not ✓**, so draw the check as a vector icon.
- Fonts: SIL Open Font License, see `FONT_LICENSE_*.txt`.

## UI rules

- Open players get surface + line colors at full strength. Finished players use doneBg / doneInk with the avatar at 60 % opacity, so the eye finds who's left.
- Tags:
  - `✓ −4` and `column cleared` use accentTint + accentText.
  - `×2 PENALTY` uses penalty + onAccent.
  - `OPEN` is dashed.
  - The `LOWEST` badge is text color on ground color, inverted.
- A penalty is always labelled in words (**×2 PENALTY**), never shown by color alone.
- The round header reads "lowest wins · ends at 100".
- Radii: 16 for cards and buttons, 7 for tags, 24 for sheets. Spacing on an 8 dp grid.
- Don't use Material default colors or components. Don't use Scan7's cyan or its Unbounded font.

## Legal

Never use Skyjo's artwork, card designs or logo, in the app or in the Play Store listing. The store text can say "score counter for Skyjo", but the name must not appear in the icon or graphics.
