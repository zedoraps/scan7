# Scan7 – Design handoff

Read together with `docs/SCREENS.md` (screen inventory + flow, unchanged) and `brand/BRAND.md`.
This file is the source of truth for **look, layout and interaction**. Where it disagrees with older drafts, this file wins.

Reference prototype: `design/reference/RoundScreen.dc.html` — an HTML prototype of the Round screen. It is **not** app code; use it to read exact sizes, colors and behaviour (search for the numbers in `renderVals()`).

---

## 1. Principles
- **Strict layout.** 8 px spacing steps, 16 px screen margin, one card radius per size class, numbers right-aligned in columns. No ad-hoc paddings.
- **Dark, high contrast, big numbers.** Used in dim rooms, phone held over cards.
- **Open players pop, done players recede.** The eye should go to who still needs scoring.
- **Fewest taps per round.** One-tap bust, one-tap scan, "Scan next" through the table.
- No Material defaults (no stock top app bar, FAB, ripple-heavy cards, Roboto). Build custom Compose components.

## 2. Tokens

### Color
| Token | Hex | Use |
|---|---|---|
| ground | `#0A1220` | Screen background |
| surface | `#15223A` | Open player card, panels, icon buttons |
| surfaceDone | `#0D1626` | Finished (scored/bust) player card |
| line | `#2C4166` | Open card border, secondary button border |
| lineSubtle | `#22344F` / `#131F33` | Ring track / done card border |
| text | `#EEF4FA` | Primary text |
| textMuted | `#93A4BC` | Labels, secondary text |
| textDone | `#7F90A8` | Name/total on finished cards |
| accent | `#38D6F5` | Primary buttons, scan buttons, scored tag text, active toggle |
| accentTint | `#12384A` | Scored tag background |
| bust | `#FF4F9A` | Bust tag fill, bust button outline/text |
| onAccent | `#0A1220` | Text/icons on cyan or magenta |
| warning | `#EEF4FA` (= text) | Destructive confirm button: 1.5 px outline + ⚠ icon + label (never cyan, never magenta, no red) |

Player colors (fixed palette, never cyan/magenta): `#FFC24B` amber, `#B39BFF` violet, `#A8E55C` lime, `#F2E3C4` sand, `#7FA8FF` sky, `#4FE0A8` mint, `#C9B28A` khaki, `#FF9C8A` salmon, `#A9B8CC` steel, `#E2F06B` chartreuse.

### Type
- **Unbounded** (600/700): round number, totals, player initials, primary button labels.
- **DM Mono** (400/500): everything else (names, labels, tags). Labels in caps use letter-spacing 0.14–0.24 em.
- Sizes used: 72 (round number, table), 32 (round number, list), 24/18/17/14 (totals by size class), 16 (buttons), 15/13/12/11/10 (text).

### Shape & spacing
- Spacing: 4, 6, 8, 12, 16, 24 px. Screen margin 16. Header top 48, header height 48.
- Radii: cards 18 (large) / 14 (medium & compact); buttons 16 (bottom) / 12 (icon) / 8–10 (in-card actions); tags 6–8; pill toggle 14 outer / 10 inner.
- Touch targets ≥ 48 px except in-card action buttons on compact sizes (≥ 36 px) – those always have a full-size alternative ("Scan next", tap card).

## 3. Components

### Player token
Small card-shaped rectangle in the player's color, rotated −8°, radius 5, initial in Unbounded 700 in `onAccent`.

### Score ring
Circle around the token showing progress to the target (total / 200). Track `lineSubtle`, progress stroke = player color, round caps, starts at 12 o'clock. Stroke ≈ 7 % of diameter.

### Status tag
- Open: dashed 1.5 px `#3A5075` outline, text "OPEN" muted (only shown in arrange mode / compact cards).
- Scored: `accentTint` fill, `accent` text, "✓ +46".
- Bust: `bust` fill, `onAccent` text, "✕ BUST". Never color alone.

### Confirm dialog
`Scan7ConfirmDialog`: the dialog card (surface, line border, radius 18, padding 24, max 360) centred over a dim scrim; title (Unbounded 600 17), optional one-line body (14, "BUST" magenta), then **[Cancel] [Action]** 56 high, weights 1 : 1.4, fixed. Action = cyan primary, or for destructive actions the **warning button**: transparent, 1.5 px `warning` outline, ⚠ icon + label in `warning`. Confirm is armed 400 ms after opening (double-tap guard). Never inline in a sheet. List: docs/APP_FLOW.md "Confirmations".

### Menu sheet
Bottom sheet with a list (M3 `ListItem`, no outlines, 20 px line icons, ≥ 56 px rows, press dim); settings inside are M3 controls (scan direction: segmented buttons, selected = accent). Leaving items slide the sheet down first; destructive items use the warning style.

### Leader badge
24 px white circle with a dark crown icon, overlapping the card's top-right corner (table) or after the name (list).

### Player card – table view (3 size classes)
| Players | Card W×H | Ring | Token | Total | In-card actions |
|---|---|---|---|---|---|
| ≤ 4 | 112×140 | 56 | 24×32 | 17 | Scan + Bust, 36 high |
| 5–6 | 96×124 | 46 | 20×27 | 15 | Scan + Bust, 30 high |
| 7–10 | 72×96 | 36 | 16×21 | 13 | none (tag only) |
Layout: ring+token, then "Name Total" on one line, then actions (open) or tag (done).

### Player row – list view
| Players | Row height | Gap | Ring | Total |
|---|---|---|---|---|
| ≤ 6 | 72 | 8 | 48 | 24 |
| 7–10 | 48 | 6 | 32 | 18 |
Columns: ring | name (+leader badge) | total (right-aligned) | actions (Scan, Bust) or tag.

### Card states
- **Open:** `surface` bg, `line` border, shadow `0 8 18 rgba(0,0,0,.35)`, full opacity.
- **Done (scored/bust):** `surfaceDone` bg, done border, no shadow, ring+token at 45 % opacity, name/total `textDone`, tag fully bright.
- **Dragging:** 2 px `accent` border, scale 1.06, shadow `0 18 30 rgba(0,0,0,.55)`.
- **Arrange mode:** dashed border, wiggle (rotate ±1.6°, ~0.25 s alternate, staggered per card), actions hidden.

## 4. Round screen (main screen)

### Header (top 48, height 48)
App icon (32) · **view toggle** (pill: Table | List, active segment cyan) · Arrange button · Menu.
In arrange mode the header becomes: "Arrange seats" + hint ("Drag players to where they sit" / "Drag rows into seating order") + **Done** (cyan).

### Table view
- Cards placed in a ring (ellipse) around the screen center, evenly spaced in seating order, starting at the top, clockwise. Area: y 112–704.
- Center: "ROUND" label + round number (72).
- **No table graphic.**
- Positions are **free**: in arrange mode a card can be dragged anywhere (clamped to the area); it stays where dropped. Positions are saved per game (and can default from the previous game with the same players).

### List view
- Row "ROUND 4" at top (y 112), rows from y 168 in seating order.
- In arrange mode rows are dragged vertically; dropping swaps into that position.
- Seating order is **shared** between views (list order = clockwise order around the table).
- Default view: Table for ≤ 6 players, List for 7+. User choice is remembered.

### Progress strip (y 720, height 24, above the bottom buttons)
Left: one skewed segment per player in seating order (scored = player color, bust = magenta, open = outline). Right: "2 of 4 scored · to 200".

### Bottom buttons (bottom 24, height 64)
- While someone is open: `[Close round]` secondary (88 wide) + **`[SCAN NEXT · Ben ▢]`** primary (shows next open player in seating order with their token).
- When everyone is done: one full-width **Close round** primary.

### Interactions
- Tap a card's **Scan** → Camera for that player. **Bust** → marks bust immediately (undo via snackbar-style toast "Anna marked bust · Undo").
- Tap an open card body → player sheet (scan / manual / bust). Tap a done card → its result (edit / rescan / reset).
- **Long-press (≈450 ms) any card → arrange mode** (like rearranging app icons), drag continues immediately. Arrange button does the same.
- **Scan next:** after Review → OK, go straight to the camera for the next open player ("Next: Ben"), with a "Back to table" option. Ends on the Round screen when nobody is open.
- Animate cards to new positions over ~220 ms ease-out.

## 5. Other screens
Keep the flow and content from `docs/SCREENS.md`. Restyle them with these tokens and components (they were drafted before this direction):
- Player sheet → bottom sheet using the same card styling; primary Scan (cyan, tall), Manual, Bust (magenta outline).
- Camera → live boxes cyan 3 px + 1.5 px white outline, label tag top-left in cyan; ignored cards dashed grey "IGNORED"; header with player token + "Scanning Mia"; big shutter; "Type instead".
- Review → bottom panel: player, total big in cyan, breakdown line, card chips, buttons Retake (icon) · Fix · OK (primary). Bust variant: magenta header band "✕ BUST · Two 7s", duplicate chips outlined magenta, buttons Fix · Confirm bust (magenta). 7-different variant: "+15" badge.
- End of round → same table/list layout showing round points per card, rank badges, rings advanced, "Next round" primary.

## 6. Accessibility
- Each card: content description "Mia, 96 points, not scored yet". Actions labelled "Scan Mia", "Mark Mia as bust".
- Arrange mode reachable without long-press (Arrange button); TalkBack users can reorder in list view with move up/down actions.
- Contrast ≥ 4.5:1 for text; status never by color alone.
