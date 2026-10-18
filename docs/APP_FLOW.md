# Scan7 – App flow

> Agreed 2026-10-18. This is the spec for the game screens; the current debug screens
> (live camera, score tester, detect from photo) stay available for testing.

## Players
- Players are stored locally on the phone: name + color or emoji. Add, rename, delete any time.
- No groups.

## New game
1. Select who plays tonight from the stored players (checkboxes). A new player can be added right here and is saved to the list.
2. Put the selected players in seating order (drag to reorder).
3. Target score: 200 (default, changeable).
4. Start → round 1.

Games, rounds and scores are persisted, so an unfinished game survives closing the app and can be resumed.

## Round screen
Shown during the whole round: all players in seating order, each with
- name, total so far,
- this round's status: `open`, `✓ +46` (scored), or `✕ BUST` (magenta **and** the word BUST).

Header: round number and current leader. Bottom: **Close round**.

Tap a player → sheet with **Scan**, **Enter manually**, **Bust**. For an already scored or busted player also **Edit** and **Reset to open**.

## Scan
1. Full-screen camera, player name at the top, live boxes on the cards.
2. **Shutter button only** (no auto-capture). Pressing it freezes the current frame.
3. Review overlay on the frozen frame: boxes and labels stay on the cards; a bottom panel shows
   - detected cards as chips,
   - score breakdown (numbers, x2, plus cards, Flip 7 bonus) and the total,
   - buttons **Retake** (back to camera), **Fix** (manual screen, prefilled), **✓ OK** (save, back to round screen).
4. If a number is detected twice, the panel shows **BUST · two 7s** (magenta + text) with **Confirm bust** and **Fix**.

## Manual screen
Used for **Enter manually**, **Fix** (prefilled with the scanned cards) and **Edit**.
- Top: selected cards as chips; tap a chip to remove it.
- Grid: `0`–`12`, `+2` `+4` `+6` `+8` `+10` `x2`. Tapping toggles a card on/off (each card counts at most once per player).
- Action cards and `back` are not in the grid (they never score). If the scan found them, they show as grey "ignored" chips.
- Live score breakdown at the bottom, **Save**.

## Close round
1. Confirmation: "X and Y haven't been scored. They'll be marked BUST." → every open player becomes busted (0 points).
2. Round summary: points this round and new totals.
3. If at least one player has reached the target (≥ 200) **after the full round is scored**, the game ends: the player with the most points wins (exact tie → shared win). Reaching 200 mid-round never ends the round early.
4. Otherwise → next round, all players `open`.

Closed rounds can be reopened from the scoreboard and edited; totals and the game-over state are recomputed.

## Stats
Store everything needed for statistics later (games, rounds, per-player round results incl. bust, and the confirmed cards per row), but no stats UI for now.
