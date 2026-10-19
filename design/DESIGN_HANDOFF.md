# Scan7 design handoff

Read it together with `docs/dev/SCREENS.md` (screen inventory and flow, unchanged) and `brand/BRAND.md`.
This file is the source of truth for **look, layout and interaction**. Where it disagrees with older drafts, this file wins.

The reference prototype `design/reference/RoundScreen.dc.html` is an HTML prototype of the Round screen. It is **not** app code. Use it to read exact sizes, colors and behaviour (search for the numbers in `renderVals()`).

---

## 1. Principles
- **Strict layout.** 8 px spacing steps, 16 px screen margin, one card radius per size class, numbers right-aligned in columns. No ad-hoc paddings.
- **Dark, high contrast, big numbers.** Used in dim rooms, phone held over cards.
- **Open players stand out, done players are dimmed.** The eye should go to who still needs scoring.
- **Fewest taps per round.** Bust and scan take one tap each, and "Scan next" goes through the table.
- No Material defaults (no stock top app bar, FAB, ripple-heavy cards or Roboto). Build custom Compose components.

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

Player colors are never cyan or magenta. They are the 15 avatar colors in design/avatars/avatar_catalog.json, and the first ten are the original palette: `#FFC24B` amber, `#B39BFF` violet, `#A8E55C` lime, `#F2E3C4` sand, `#7FA8FF` sky, `#4FE0A8` mint, `#C9B28A` khaki, `#FF9C8A` salmon, `#A9B8CC` steel, `#E2F06B` chartreuse.

### Type
- **Unbounded** (600/700): round number, totals, player initials, primary button labels.
- **DM Mono** (400/500): everything else (names, labels, tags). Labels in caps use letter-spacing 0.14–0.24 em.
- Sizes used: 72 (round number, table), 32 (round number, list), 24/18/17/14 (totals by size class), 16 (buttons), 15/13/12/11/10 (text).

### Shape & spacing
- Spacing: 4, 6, 8, 12, 16, 24 px. Screen margin 16. Header top 48, header height 48.
- Radii: cards 18 (large) / 14 (medium & compact); buttons 16 (bottom) / 12 (icon) / 8–10 (in-card actions); tags 6–8; pill toggle 14 outer / 10 inner.
- Touch targets are ≥ 48 px, except in-card action buttons on compact sizes (≥ 36 px). Those always have a full-size alternative ("Scan next", tap the card).

## 3. Components

### Player token (now avatar)
The player **avatar** replaces the token (design/avatars/AVATARS.md: tinted card, pattern and face, tilted −8°, `PlayerAvatar`). Its sizes are the token heights below. The original token was a small card-shaped rectangle in the player's color, rotated −8°, radius 5, with the initial in Unbounded 700 in `onAccent`.

### Score ring
A circle around the token shows progress to the target (total / 200). The track is `lineSubtle`, the progress stroke is the player color with round caps, and it starts at 12 o'clock. The stroke is ≈ 7 % of the diameter.

### Status tag
- Open: dashed 1.5 px `#3A5075` outline, text "OPEN" muted (only shown in arrange mode / compact cards).
- Scored: `accentTint` fill, `accent` text, "✓ +46".
- Bust: `bust` fill, `onAccent` text, "✕ BUST". Never color alone.

### Confirm dialog
`Scan7ConfirmDialog` is the dialog card (surface, line border, radius 18, padding 24, max 360), centred over a dim scrim. It has a title (Unbounded 600 17), an optional one-line body (14, "BUST" magenta), then a fixed **[Cancel] [Action]** row, 56 high, weights 1 : 1.4. The action is the cyan primary or, for destructive actions, the **warning button**: transparent, 1.5 px `warning` outline, ⚠ icon and label in `warning`. Confirm is armed 400 ms after opening, as a double-tap guard. The dialog is never inline in a sheet. The list of confirmations is in docs/dev/APP_FLOW.md "Confirmations".

### Menu sheet
A bottom sheet with a list (M3 `ListItem`, no outlines, 20 px line icons, ≥ 56 px rows, dimmed on press). Settings inside are M3 controls (scan direction uses segmented buttons, with the selected one in accent). Items that leave the sheet slide it down first. Destructive items use the warning style.

### Leader badge
A 24 px white circle with a dark crown icon. It overlaps the card's top-right corner (table) or follows the name (list).

### Player card, table view (3 size classes)
| Players | Card W×H | Ring | Token | Total | In-card actions |
|---|---|---|---|---|---|
| ≤ 4 | 112×140 | 56 | 24×32 | 17 | Scan + Bust, 36 high |
| 5–6 | 96×124 | 46 | 20×27 | 15 | Scan + Bust, 30 high |
| 7–10 | 72×96 | 36 | 16×21 | 13 | none (tag only) |
The card shows ring and token, then "Name Total" on one line, then the actions (open) or the tag (done).

### Player row, list view
| Players | Row height | Gap | Ring | Total |
|---|---|---|---|---|
| ≤ 6 | 72 | 8 | 48 | 24 |
| 7–10 | 48 | 6 | 32 | 18 |
The columns are ring | name (with leader badge) | total (right-aligned) | actions (Scan, Bust) or tag.

### Card states
- **Open:** `surface` bg, `line` border, shadow `0 8 18 rgba(0,0,0,.35)`, full opacity.
- **Done (scored/bust):** `surfaceDone` bg, done border, no shadow, ring and token at 45 % opacity, name/total `textDone`, tag fully bright.
- **Dragging:** 2 px `accent` border, scale 1.06, shadow `0 18 30 rgba(0,0,0,.55)`.
- **Arrange mode:** dashed border, wiggle (rotate ±1.6°, ~0.25 s alternate, staggered per card), actions hidden.

## 4. Round screen (main screen)

### Header (top 48, height 48)
App icon (32) · **view toggle** (pill: Table | List, active segment cyan) · Arrange button · Menu.
In arrange mode the header shows "Arrange seats", a hint ("Drag players to where they sit" / "Drag rows into seating order") and **Done** (cyan).

### Table view
- Cards sit in a ring (ellipse) around the screen center, evenly spaced in seating order, starting at the top and going clockwise. The area is y 112–704.
- The center shows the "ROUND" label and the round number (72).
- **No table graphic.**
- Positions are **free**. In arrange mode a card can be dragged anywhere (clamped to the area), and it stays where dropped. Positions are saved per game and can default from the previous game with the same players.

### List view
- The row "ROUND 4" is at the top (y 112), and player rows start at y 168 in seating order.
- In arrange mode, rows are dragged vertically. Dropping a row swaps it into that position.
- Both views **share** the seating order. The list order is the clockwise order around the table.
- The default view is Table for up to 6 players and List for 7 or more. The app remembers the user's choice.

### Progress strip (y 720, height 24, above the bottom buttons)
On the left, one skewed segment per player in seating order (scored in the player color, bust in magenta, open as an outline). On the right, "2 of 4 scored · to 200".

### Bottom buttons (bottom 24, height 64)
- While someone is open, `[Close round]` secondary (88 wide) and **`[SCAN NEXT · Ben ▢]`** primary, which shows the next open player in seating order with their token.
- When everyone is done, one full-width **Close round** primary.

### Interactions
- A card's **Scan** opens the camera for that player. **Bust** marks the player bust immediately, with undo via a snackbar-style toast "Anna marked bust · Undo".
- Tapping an open card body opens the player sheet (scan / manual / bust). Tapping a done card shows its result (edit / rescan / reset).
- **Long-press (≈450 ms) on any card enters arrange mode** (like rearranging app icons), and the drag continues immediately. The Arrange button does the same.
- **Scan next.** After OK on the Review, the app goes straight to the camera for the next open player ("Next: Ben"), with a "Back to table" option. It ends on the Round screen when nobody is open.
- Cards animate to new positions over ~220 ms ease-out.

## 5. Other screens
Keep the flow and content from `docs/dev/SCREENS.md`, and restyle the screens with these tokens and components (they were drafted before this direction).
- **Player sheet.** A bottom sheet with the same card styling: primary Scan (cyan, tall), Manual, Bust (magenta outline).
- **Camera.** Live boxes in cyan 3 px with a 1.5 px white outline and a cyan label tag top left; ignored cards dashed grey "IGNORED"; a header with the player token and "Scanning Mia"; a big shutter; "Type instead".
- **Review.** A bottom panel with the player, the total big in cyan, the breakdown line, card chips and the buttons Retake (icon) · Fix · OK (primary). The bust variant has a magenta header band "✕ BUST · Two 7s", duplicate chips outlined in magenta and the buttons Fix · Confirm bust (magenta). The 7-different variant has a "+15" badge.
- **End of round.** The same table/list layout, showing round points per card, rank badges, advanced rings and "Next round" as primary.

## 6. Accessibility
- Each card has the content description "Mia, 96 points, not scored yet". Its actions are labelled "Scan Mia" and "Mark Mia as bust".
- Arrange mode is reachable without long-press (Arrange button). TalkBack users can reorder in list view with move up/down actions.
- Text contrast is ≥ 4.5:1. Status is never shown by color alone.
