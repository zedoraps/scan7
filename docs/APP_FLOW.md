# Scan7 – App flow

> Agreed 2026-10-18. This is the spec for the game **flow**; look, layout and interaction are defined in
> [../design/DESIGN_HANDOFF.md](../design/DESIGN_HANDOFF.md), which wins where they differ. The current debug
> screens (live camera, score tester, detect from photo) stay available for testing.

## Players
- Players are stored locally on the phone: name + a color from the fixed 10-color palette (shown as a token with their initial). Add, rename, delete any time. Max 10 players per game.
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

Views: **Table** (cards placed freely around the screen, arranged by long-press/drag) and **List** (rows in seating order); seating order is shared. Default Table for ≤ 6 players, List for 7+; choice remembered. Card positions are saved per game.

Open cards have in-card **Scan** and **Bust** buttons: Bust marks the player busted immediately, with an "Undo" toast. Tap an open card → sheet with **Scan**, **Enter manually**, **Bust**. Tap a done card → its result with **Edit**, **Rescan**, **Reset to open**.

Bottom: **Scan next · <next open player>** (primary) + **Close round**; when everyone is done, one full-width **Close round**.

## Scan
1. Full-screen camera, player name at the top, live boxes on the cards.
2. **Shutter button only** (no auto-capture). Pressing it freezes the current frame.
   Next to the shutter: **Bust** (magenta outline + word) marks the player busted without scanning (Undo toast); in Scan next it continues straight to the next open player. The header shows the player big (token + name in Unbounded ~28 sp, "SCANNING" / "NEXT").
3. Review overlay on the frozen frame: boxes and labels stay on the cards; a bottom panel shows
   - detected cards as chips,
   - score breakdown (numbers, x2, plus cards, Flip 7 bonus) and the total,
   - buttons **Retake** (back to camera), **Fix** (manual screen, prefilled), **✓ OK** (save).
   - tapping a box or its chip **leaves that card out** (e.g. a neighbour's card in the frame): dashed grey "EXCLUDED", chip struck through; score, bust check and the saved cards ignore it; tap again to include it. Fix doesn't prefill left-out cards.
   After OK: if the scan was started via **Scan next**, go straight to the camera for the next open player ("Next: Ben", with "Back to table"); otherwise back to the round screen.
4. If a number is detected twice, the panel shows **BUST · two 7s** (magenta + text) with **Confirm bust** and **Fix**.
   **Second Chance rule** (in `scoreRow`): each `second_chance` card in the row (not left out) cancels one extra copy of a number; if every extra copy is covered, it is no bust, the duplicated number counts once (also for Flip 7), and review/manual show the cyan line "Second Chance used · 7 counted once".
5. **Leave a card out:** tapping a box on the photo or its chip toggles it *excluded* (dashed grey, "EXCLUDED"), e.g. a neighbour's card. Excluded cards don't count for the score or the bust check and aren't prefilled in Fix. They are still saved as real cards for training.

## Manual screen
Used for **Enter manually**, **Fix** (prefilled with the scanned cards) and **Edit**.
- Top: selected cards as chips; tap a chip to remove it.
- Grid: `0`–`12`, `+2` `+4` `+6` `+8` `+10` `x2`. Tapping adds a card once or removes one copy: the grid never adds a second copy, but a duplicate prefilled by Fix shows "×2" (magenta while it busts) and each tap removes one copy, so the user can keep one 12.
- **At most 7 number cards** (duplicates count): once 7 are selected, the other number buttons are disabled ("7 number cards – the maximum"). The review blocks OK with a warning if a scan includes more than 7 (leave cards out or Fix).
- Action cards and `back` are not in the grid (they never score). If the scan found them, they show as grey "ignored" chips.
- Live score breakdown at the bottom, **Save**.

## Close round
1. Confirmation: "X and Y haven't been scored. They'll be marked BUST." → every open player becomes busted (0 points).
2. End of round: the same table/list layout showing each player's round points (or BUST), rank badges and advanced score rings; **Next round** (primary).
3. If at least one player has reached the target (≥ 200) **after the full round is scored**, the game ends: the player with the most points wins (exact tie → shared win). Reaching 200 mid-round never ends the round early.
4. Otherwise → next round, all players `open`.

Closed rounds can be reopened from the scoreboard and edited; totals and the game-over state are recomputed.

On the end-of-round view, tapping a player card opens that player's result sheet (Edit / Rescan), not the editor directly. A muted hint "Tap a player to edit" sits above **Next round**; edits there update totals, ranks and the game-over state.

## Adjust total
Player sheet (any state, also on the end-of-round view) → **Adjust total**: a small dialog with a signed number (stepper −10 −5 −1 +1 +5 +10, ± sign toggle, or typed: `+10`, `-12`; max ±999), an optional note ("penalty", "house rule") and a preview "Total 123 → 133".
- Stored as its own entry (table `score_adjustments`, DB v3), not as a round result; it records the round that was running.
- The sheet lists the player's adjustments (delta, note, round) with ✕ to remove one (inline **Undo**).
- All totals include adjustments (round cards/rows, leader, progress, scoreboard TOTAL row, game over, resume card) via `GameState.totals`. No separate scoreboard row: an adjusted total shows a tiny "±".
- Game over is unchanged: only checked once the round is closed (an adjustment mid-round never ends the game; on the end-of-round view totals are re-checked like any edit).

## End game early
Round menu (⋮) → **End game** → confirm → the current round is closed as it is (open players BUST) → Game over; the current leader(s) win (tie → shared win).

## Debug menu
Hidden in every build: tap the Scan7 wordmark on Home 5 times (a toast counts down after the 3rd tap; TalkBack action "Open debug menu"). "Save scans for training" lives there and is ON by default.

## Camera
Shutter only; best frame of the last second is used. Torch toggle in the camera header (off by default).

## Stats
Store everything needed for statistics later (games, rounds, per-player round results incl. bust, and the confirmed cards per row), but no stats UI for now.
