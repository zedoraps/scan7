# Scan7 screens

This is the screen inventory. [APP_FLOW.md](APP_FLOW.md) specifies the behaviour, and [../brand/BRAND.md](../../brand/BRAND.md) has the brand rules.
[../design/DESIGN_HANDOFF.md](../../design/DESIGN_HANDOFF.md) defines look, layout, tokens and components, and **it wins over this file where they differ**. For example, players are a palette color and an initial, not an emoji; bust takes one tap with undo; the end of round reuses the table/list layout.

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
- **Dark theme** with the palette "Deep Cyan" (ground `#0A1220`, surface `#15223A`, text `#EEF4FA`, muted `#93A4BC`, accent `#38D6F5`, bust `#FF4F9A`). People play in dim rooms.
- **Bust is magenta together with the word "BUST" (and/or an icon)**, never color alone, and never orange or red (those are card colors).
- Unbounded Bold is the font for the wordmark and big numbers (scores, totals). UI text uses DM Mono or the system font.
- **The app is used at the table**, with one hand, the phone held over the cards, often in dim light. It needs big touch targets (≥ 48 dp), key actions at the bottom within thumb reach, high contrast and large score numbers.
- **Players are told apart by color/emoji and name** everywhere they appear.
- Never use Flip 7's artwork, fonts or logo.

---

## 1 Home
The entry point.
- Scan7 wordmark.
- **Resume game** (if one is unfinished) with round number, players' avatars and leader.
- **Past games** (game history): finished, ended-early and abandoned games, newest first, each with date, avatars, winner and points, and rounds. A tap opens 11 Scoreboard of that game (read-only for abandoned games).
- **New game** (primary).
- **Players** (manage list).
- **Debug** is hidden: tap the wordmark 5 times.
- ⋮ top right opens the version line, About & licenses (version and model details) and Language (System default / English / Deutsch). No footer.

States: no unfinished game (history only, or the intro when there is no game at all); no players yet (New game leads to adding players first).

## 2 Players
Manages the stored players.
- List rows show the avatar (color/emoji) and name. A tap opens 2a. Swipe or the menu deletes a player after a confirmation.
- **Add player** button.

States: empty list ("Add your first player").

### 2a Player editor (sheet or dialog)
- Name field.
- Color picker (fixed palette) and/or emoji picker.
- Save / Delete.

Note: player colors must stay distinguishable from accent cyan and bust magenta.

## 3 New game, pick players
- All stored players with checkboxes (avatar and name).
- **+ New player** inline. It opens 2a, and the new player is selected automatically.
- A compact **target** row (default **200**, − / + in steps of 10), pinned above the buttons.
- **Next** (primary, full width), enabled with 2–10 players. Arranging seats on the next step is optional.

### 3b Seats
- The round screen's table or list (same cards and rows) in arrange mode, with seat number badges 1…n.
- The header is ← · "Seats" · Table | List toggle. In the table, drag cards to where people sit. In the list, drop a row on another to swap the two.
- The bottom has a drag hint and **Start game** (primary). The positions carry over into the game's table view.

## 4 Round screen (main screen)
Shown during the whole round.
- Header: **Round N**, the current leader (name and total), the target (e.g. "to 200").
- One row or card per player in seating order:
  - avatar and name,
  - **total** (big number),
  - this round's status:
    - `open` (muted),
    - `✓ +46` (accent),
    - `✕ BUST` (magenta and the word).
  - Optional: a thin progress bar toward 200.
- Bottom: **Close round** (primary once at least one player is scored; always available).
- Top right: scoreboard/history (11) and the menu (end game, settings).

States: all open (start of round); mixed; all scored ("Close round" highlighted); someone at 200 or more (highlighted, but the round continues).

### 4a Player sheet (bottom sheet)
- Player avatar and name, total.
- If `open`: **Scan** (primary), **Enter manually**, **Bust**.
- If scored or busted: the current result, **Edit** (opens 7), **Rescan** (opens 5), **Reset last entry**.

## 5 Camera
The player points the camera at their row and captures it.
- Full-screen live camera.
- Top: player avatar and name ("Scanning Ben"), close/back.
- Live boxes on the cards (cyan with white outline) with small labels.
- Optional hint: "Fit the whole row in the frame".
- Bottom: **shutter button** (big, centered). Optional: a torch toggle.

States: camera permission missing (explain, then grant or open settings); no cards detected (the shutter still works, and the review shows "no cards found"); low light hint (optional).

## 6 Review (overlay on the frozen frame)
The player checks the scan before saving.
- Frozen photo with boxes and labels.
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
Enters or corrects a player's cards (from Enter manually, Fix, Edit).
- Header: player avatar and name; Cancel.
- Selected cards as chips at the top (tap to remove).
- Card grid, toggle on/off:
  - numbers `0`–`12`,
  - modifiers `+2` `+4` `+6` `+8` `+10` `×2`.
  - (no action cards, no `back`)
- Ignored cards from the scan shown greyed (info only).
- Bottom: live breakdown and total, **Save** (primary).

Design note: the grid is the most used input. Its cards could echo the card colors **abstractly** (number color dots, orange-ish modifiers) but must not copy Flip 7's card art.

## 8 Close-round dialog
- "Close round N?"
- If players are still open: "Anna and David haven't been scored. They'll be marked BUST." (names with avatars).
- **Cancel** / **Close round**.

## 9 Round summary
- Round N results per player: round points (or BUST), new total, rank change (optional).
- Leader highlighted.
- **Next round** (primary). If the game is over, it goes to 10 instead.
- Edit link (reopens this round).

## 10 Game over
- The winner or winners big (avatar, name, total); a shared win if tied.
- Final standings.
- **New game** (same players, same order, with 3b prefilled) / **Home**.
- Link to the scoreboard (11).

## 11 Scoreboard / history
- A table with one row per round and one column per player (transposed on narrow screens). Cells hold points or BUST, with totals at the bottom.
- Tapping a cell opens that player's result in that round in 7 (edit). Totals and the game-over state update.

## D Debug screens (existing)
Live camera with FPS/inference overlay, detect from photo, and the "Save scans for training" toggle. They are not part of the designed flow but stay reachable for testing.

---

## Shared components to design
- Player avatar (color/emoji and initial) in sizes S/M/L.
- Status pill: open / scored (+N) / BUST.
- Big score number (Unbounded).
- Card chip (number, modifier, ignored).
- Card grid button (off / on).
- Primary / secondary / destructive buttons; bottom sheet; dialog.
- Detection box and label (camera, review).
