# Scan7 – Screens

Screen inventory. Behaviour is specified in [APP_FLOW.md](APP_FLOW.md); brand rules in [../brand/BRAND.md](../brand/BRAND.md).
**Look, layout, tokens and components: [../design/DESIGN_HANDOFF.md](../design/DESIGN_HANDOFF.md) — it wins over this file where they differ** (e.g. players are a palette color + initial, no emoji; one-tap bust with undo; end of round reuses the table/list layout).

## Flow map

```
                    ┌────────────┐
                    │ 1 Home     │──────────────► 2 Players ──► 2a Player editor
                    └─────┬──────┘
             new game     │      resume
          ┌───────────────┴───────────────┐
          ▼                               │
   3 New game: pick players               │
          ▼                               │
   3b Seats (table / list arrange)        │
          ▼                               ▼
   ┌──────────────────────────────────────────┐
   │ 4 Round screen                           │◄────────────┐
   └───┬───────────────────────────┬──────────┘             │
       │ tap player                │ Close round            │
       ▼                           ▼                        │
   4a Player sheet            8 Close-round dialog          │
   │   │      │                    ▼                        │
   │   │      └─ Bust ──► (back to 4)   9 Round summary ────┤ next round
   │   │                                     │              │
   │   └─ Enter manually ──► 7 Manual ──► (back to 4)        │
   │                             ▲               ▼          │
   └─ Scan ──► 5 Camera ──► 6 Review ──Fix──┘   10 Game over
                   ▲            │ OK ──► (back to 4)
                   └─ Retake ───┘

   11 Scoreboard / history (from 1, 4 and 10) ──► edit a past round ──► 7 Manual
   D  Debug screens (from 1, hidden or in a menu)
```

## Design constraints (all screens)
- **Dark theme**, palette "Deep Cyan" (ground `#0A1220`, surface `#15223A`, text `#EEF4FA`, muted `#93A4BC`, accent `#38D6F5`, bust `#FF4F9A`). People play in dim rooms.
- **Bust = magenta + the word "BUST" (and/or an icon)**, never color alone, never orange/red (those are card colors).
- **Fonts:** Unbounded Bold for the wordmark and big numbers (scores, totals); DM Mono or system font for UI text.
- **Used at the table:** one hand, phone held over cards, often dim light. Big touch targets (≥ 48 dp), key actions at the bottom within thumb reach, high contrast, large score numbers.
- **Players are told apart by color/emoji + name** everywhere they appear.
- Never use Flip 7's artwork, fonts or logo.

---

## 1 Home
**Purpose:** entry point.
- Scan7 wordmark.
- **Resume game** (if one is unfinished): round number, players' avatars, leader.
- **Past games** (game history): finished / ended-early / abandoned games, newest first — date, avatars, winner + points, rounds. Tap → 11 Scoreboard of that game (abandoned: read-only).
- **New game** (primary).
- **Players** (manage list).
- **Debug** is hidden: tap the wordmark 5 times.
- Footer: app version, model version.

States: no unfinished game (history only, or the intro when there is no game at all); no players yet (New game leads to adding players first).

## 2 Players
**Purpose:** manage the stored players.
- List: avatar (color/emoji), name. Tap → 2a. Swipe or menu → delete (confirm).
- **Add player** button.

States: empty list ("Add your first player").

### 2a Player editor (sheet or dialog)
- Name field.
- Color picker (fixed palette) and/or emoji picker.
- Save / Delete.

Note: player colors must stay distinguishable from accent cyan and bust magenta.

## 3 New game – pick players
- All stored players with checkboxes (avatar + name).
- **+ New player** inline (opens 2a, new player is auto-selected).
- Compact **target** row (default **200**, − / + in steps of 10), pinned above the buttons.
- **Next** (primary, full width), enabled with 2–10 players. Arranging seats on the next step is optional.

### 3b Seats
- The round screen's table / list (same cards and rows) in arrange mode, seat number badges 1…n.
- Header: ← · "Seats" · Table | List toggle. Table: drag cards to where people sit; list: drop a row on another to swap.
- Bottom: drag hint + **Start game** (primary). Positions carry over into the game's table view.

## 4 Round screen  ★ main screen
**Purpose:** what's shown during the whole round.
- Header: **Round N**, current leader (name + total), target (e.g. "to 200").
- One row/card per player in seating order:
  - avatar + name,
  - **total** (big number),
  - this round's status:
    - `open` (muted),
    - `✓ +46` (accent),
    - `✕ BUST` (magenta + word).
  - Optional: thin progress bar toward 200.
- Bottom: **Close round** (primary once at least one player is scored; always available).
- Top-right: scoreboard/history (11), menu (end game, settings).

States: all open (start of round); mixed; all scored ("Close round" highlighted); someone ≥ 200 (highlight, but the round continues).

### 4a Player sheet (bottom sheet)
- Player avatar + name, total.
- If `open`: **Scan** (primary), **Enter manually**, **Bust**.
- If scored or busted: current result, **Edit** (→ 7), **Rescan** (→ 5), **Reset last entry**.

## 5 Camera
**Purpose:** point at the player's row and capture.
- Full-screen live camera.
- Top: player avatar + name ("Scanning Ben"), close/back.
- Live boxes on the cards (cyan with white outline) with small labels.
- Optional hint: "Fit the whole row in the frame".
- Bottom: **shutter button** (big, centered). Optional: torch toggle.

States: camera permission missing (explain + grant / open settings); no cards detected (shutter still works; review shows "no cards found"); low light hint (optional).

## 6 Review (overlay on the frozen frame)
**Purpose:** check the scan before saving.
- Frozen photo with boxes + labels.
- Bottom panel:
  - player name,
  - **total** (big),
  - breakdown line: `7 + 12 + 3 = 22 · ×2 = 44 · +4 · Flip 7 +15`,
  - detected cards as chips (scoring cards normal, action cards/`back` grey "ignored"),
  - buttons: **Retake**, **Fix**, **OK** (primary).

Variants:
- **Bust detected** (duplicate number): panel in magenta with **BUST · two 7s**, buttons **Fix** and **Confirm bust**.
- **Flip 7** (7 different numbers): celebrate the +15 bonus.
- **Nothing detected:** "No cards found", buttons **Retake** and **Enter manually**.

## 7 Manual entry
**Purpose:** enter or correct a player's cards (from Enter manually, Fix, Edit).
- Header: player avatar + name; Cancel.
- Selected cards as chips at the top (tap to remove).
- Card grid, toggle on/off:
  - numbers `0`–`12`,
  - modifiers `+2` `+4` `+6` `+8` `+10` `×2`.
  - (no action cards, no `back`)
- Ignored cards from the scan shown greyed (info only).
- Bottom: live breakdown + total, **Save** (primary).

Design note: the grid is the most used input; cards could echo the card colors **abstractly** (number color dots, orange-ish modifiers) but must not copy Flip 7's card art.

## 8 Close-round dialog
- "Close round N?"
- If players are still open: "Anna and David haven't been scored. They'll be marked BUST." (names with avatars).
- **Cancel** / **Close round**.

## 9 Round summary
- Round N results per player: round points (or BUST), new total, rank change (optional).
- Leader highlighted.
- **Next round** (primary) — or, if the game is over, goes to 10.
- Edit link (reopen this round).

## 10 Game over
- Winner(s) big (avatar, name, total); shared win if tied.
- Final standings.
- **New game** (same players, same order — prefilled 3b) / **Home**.
- Link to scoreboard (11).

## 11 Scoreboard / history
- Table: rows = rounds, columns = players (or transposed on narrow screens), cells = points or BUST, totals at the bottom.
- Tap a cell → that player's result in that round → 7 (edit). Totals and game-over state update.

## D Debug screens (existing)
Live camera with FPS/inference overlay, detect from photo, "Save scans for training" toggle. Not part of the designed flow; keep reachable for testing.

---

## Shared components to design
- Player avatar (color/emoji + initial) in sizes S/M/L.
- Status pill: open / scored (+N) / BUST.
- Big score number (Unbounded).
- Card chip (number, modifier, ignored).
- Card grid button (off / on).
- Primary / secondary / destructive buttons; bottom sheet; dialog.
- Detection box + label (camera, review).
