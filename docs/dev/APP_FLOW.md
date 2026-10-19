# Scan7 – App flow

> Agreed 2026-10-18. This is the spec for the game **flow**; look, layout and interaction are defined in
> [../design/DESIGN_HANDOFF.md](../../design/DESIGN_HANDOFF.md), which wins where they differ. The current debug
> screens (live camera, detect from photo) stay available for testing.

## Players
- Players are stored locally on the phone: name + an **avatar** (color + pattern + face from the avatar kit, [../design/avatars/AVATARS.md](../../design/avatars/AVATARS.md): 15 colors, 6 patterns incl. plain, 12 faces; never cyan or magenta). Add, rename, delete any time. Max 10 players per game.
- The avatar replaces the old initial token everywhere (round cards inside the score ring, list rows, camera header, review/manual, sheets, scoreboard, game over, resume card, players list, new game); the name always stays visible next to it. TalkBack: "Mia, sand avatar, grin face".
- **Avatar editor** (player sheet): live preview (88 dp) + name at the top, shuffle in the header, Face 3×4, Pattern 1×6, Color 3×5; Save stays pinned at the bottom.
- **Colors may repeat within a game.** Colors used by other players of the current game (or the game being set up in New game) show a small dot so people can avoid duplicates; nothing is blocked.
- A new player gets a random face + pattern and the first color nobody uses: in New game, nobody of the selected players; in the players list, no stored player (else the first color).
- No groups.

## New game
1. Select who plays tonight from the stored players (checkboxes; tick order = seating order). A new player can be added right here and is saved to the list. Shared avatar colors don't block **Next** (no "Same color as …" check any more). The **target** (200 default, steps of 10) is a compact row pinned above the button. Bottom: one full-width **[Next]** (no "Skip seats": arranging on the Seats step is optional).
2. **Seats:** the round screen's own table / list in arrange mode (same cards, drag rules and Table | List toggle; seat numbers 1…n on the cards / rows), header ← · "Seats" · Table|List, bottom hint "Drag players to where they sit, or just start" (list: "Drag rows into seating order, or just start") + **Start game**, which works without moving anyone (tick order on the default ring, or the earlier game's spots). Table: drop a card where the player sits (order re-derived clockwise); list: drop a row on another to swap. Same players as an earlier game → that game's order and spots. The positions set here are the game's table positions; the chosen view is remembered (app preference). Back / predictive back returns to step 1 with the arrangement kept (as long as the same players stay picked). "New game with same players" opens directly on this step.
3. Start → round 1.

Games, rounds and scores are persisted, so an unfinished game survives closing the app and can be resumed.

## Round screen
Shown during the whole round: all players in seating order, each with
- name, total so far,
- this round's status: `open`, `✓ +46` (scored), or `✕ BUST` (magenta **and** the word BUST).

Header: round number and current leader (the leader badge: a white circle with a bold three-point crown, rounded tips over a base band, glyph ≈ 84 % of the badge so it reads at 20 dp). Bottom: **Close round**.

Views: **Table** (cards placed freely around the screen, arranged by long-press/drag, or **Rearrange seats / order** as the first item of the round menu ⋮; the header is logo · Table|List · ⋮) and **List** (rows in seating order); seating order is shared. Default Table for ≤ 6 players, List for 7+; choice remembered. Card positions are saved per game. The round menu is a bottom sheet (scrolls when it doesn't fit) with an M3 list, no header and no supporting texts. Actions, most used first (`ListItem` rows with icons): **Scoreboard**, **Rearrange seats / order**, **Players** (see "Players during a game"), **Home**, a divider, **End game** (warning style; its confirmation explains). Then **SETTINGS** (the sheet stays open): **Target points** (− value +, steps of 10, 50–1000, this game only), **Scan direction** (M3 segmented buttons ↻ Clockwise | ↺ Counter), **After a score** (Next player | Back to table, the same `after_scan` setting as the review's split OK ▾) and **Entry** (Scan | Type, app setting `entry_mode`).

**Target points** changes the running game's target at once (progress "to N", scoreboard subtitle, Home). Lowering it to or below the leader's total asks first ("Lower the target to N? The game ends after this round.", destructive); the game-over check at the round close uses the new target. Hidden once the game is over.

**Entry: Type** (no camera): every scan entry point (in-card Scan, Scan next, sheet Scan, double-tap) opens the manual entry directly, no camera and no camera permission prompt; results are saved with source MANUAL. The in-card button shows a keypad icon, the bottom button says "ENTER NEXT · Ben" (DE "WEITER EINGEBEN"), the player sheet's big button is "Enter manually" (no Rescan on done players; Edit covers it). The Scan next chain continues with the next player's manual entry (following "After a score").

Open cards have in-card **Scan** and **Bust** buttons: Bust marks the player busted immediately, with an "Undo" toast. Tap an open card → sheet with **Scan**, **Enter manually**, **Bust**. Tap a done card → its result with **Edit**, **Rescan**, **Reset last entry** (back to open). Compact cards (7–10 players): name on one line, total below; **double-tap** an open card to scan (TalkBack action "Scan <name>").

Bottom: **Scan next · <next open player>** (primary) + **Close round**; when everyone is done, one full-width **Next round** (closes the round and starts the next one in one tap). "Scan next" follows the **scan direction** (round menu ⋮: ↻ clockwise / ↺ counter-clockwise, app setting `scan_direction`); seating and list order don't change.

## Players during a game
Round menu (⋮) → **Players** (below Rearrange seats / order; hidden once the game is over): New game's step-1 picker (same rows, checkboxes and "+ New player"), header "Players · Round 6 · 7 of 10", preselected with the seated players; rows that change say "Joins in round 6, open" / "Leaves the game". **Done** with a changed lineup → **Seats** (the round table / list in arrange mode, Done instead of Start game) → back to the round in one transaction (`GameState.withLineup`); an unchanged lineup goes straight back. Back leaves without changes.
- **Adding:** the player joins the running round as **open**, total 0; their earlier rounds have no result and show "–" in the scoreboard (header tag "FROM R6", TalkBack "joined in round 6"). Default seat: after the last seat (between the last and the first card; if the table is still on the default ring, everyone moves to the ring of the new size). A player who had left comes back with their old results (gap rounds "–").
- **Removing:** unticking a seated player asks first (warning style, see Confirmations). They leave the running round: their result there (points or BUST) is dropped; past results and adjustments stay and count in their total; their scoreboard column moves to the end, dimmed, tag "LEFT". They no longer count for "all done", Scan next, the progress strip, leaders or the winner (game over checks seated players only; Game over lists them last as "left", unranked). Someone who joined this round and has nothing recorded just disappears.
- 2 … 10 players (`MIN_PLAYERS` … `MAX_PLAYERS`); only while a round is running.
- Stored on the seat row: `game_players.joinedRound` (default 1) and `leftRound` (null while playing), DB v5 (AutoMigration 4 → 5). Home history and the scoreboard show everyone who played (seated, then the ones who left) with their totals; "same players" (New game, previous seats) means the final seated lineup.

## Scan
1. Full-screen camera, player name at the top, live boxes on the cards.
2. **Shutter button only** (no auto-capture). Pressing it freezes the current frame.
   Left of the shutter: **Bust** (magenta outline + word) marks the player busted without scanning: the full-screen bust overlay (~1 s, with Undo) confirms it, then it continues like a save (no toast afterwards; only Back during the overlay leaves the bust with the Undo toast on the round screen). Right of the shutter: **Type** (manual entry). The top row is ← back, then the player (avatar + "SCANNING" / "NEXT" · total over the name in Unbounded ~28 sp). Tap the preview to focus there; pinch to zoom (the zoom pills follow). Zoom pills only for what the camera supports (CameraX `ZoomState`): 0.5× when the min zoom is ≤ 0.6 (ultra-wide), 1×, 2×, 5× up to the max zoom; no pill row when the max zoom is below 1.5× (or only one pill is left). A remembered zoom the camera can't do falls back to 1×.
3. Review overlay on the frozen frame: boxes and labels stay on the cards; a bottom panel shows
   - detected cards as chips,
   - score breakdown (numbers, x2, plus cards, Flip 7 bonus) and the total,
   - buttons **Retake** (back to camera), **Fix** (manual screen, prefilled), **OK** (save; split button, see below).
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
1. Confirmation (only while someone is open): "X and Y haven't been scored. They'll be marked BUST." → every open player becomes busted (0 points). If nobody has been scored at all: "Nobody has been scored yet – mark everyone BUST?" (likely an accident; destructive style).
2. The round is closed and the next one starts at once (one transaction); no toast (the scoreboard is in the round menu).
3. If at least one player has reached the target (≥ 200) **after the full round is scored**, the game ends: the player with the most points wins (exact tie → shared win). Reaching 200 mid-round never ends the round early.
4. Otherwise → next round, all players `open`.

Closed rounds can be reopened from the scoreboard and edited; totals and the game-over state are recomputed.

Results of closed rounds are edited from the scoreboard. The game-over check runs when the running round closes.

## Adjust total
Player sheet (any state, also on the end-of-round view) → **Adjust total**: a small dialog with a signed number (stepper −10 −5 −1 +1 +5 +10, ± sign toggle, or typed: `+10`, `-12`; max ±999), an optional note ("penalty", "house rule") and a preview "Total 123 → 133".
- Stored as its own entry (table `score_adjustments`, DB v3), not as a round result; it records the round that was running.
- The sheet lists the player's adjustments (delta, note, round) with ✕ to remove one (inline **Undo**).
- All totals include adjustments (round cards/rows, leader, progress, scoreboard TOTAL row, game over, resume card) via `GameState.totals`. No separate scoreboard row: an adjusted total shows a tiny "±".
- Game over is unchanged: only checked once the round is closed (an adjustment mid-round never ends the game; on the end-of-round view totals are re-checked like any edit).

## End game early
Round menu (⋮) → **End game** → confirm (see Confirmations) → the current round is closed as it is (open players BUST) → Game over; the current leader(s) win (tie → shared win).

## Confirmations
One pattern for every confirmation: `Scan7ConfirmDialog` (ui/components, on `Scan7Dialog` = M3 `BasicAlertDialog`), centred over a dim scrim, **never inline in a sheet** (the sheet underneath keeps its layout). Title "Delete Anna?", optional one-line body, fixed row **[Cancel] [Action]** (1 : 1.4). Back / scrim tap = Cancel. TalkBack: pane title = title, focus starts on the title. Confirm ignores taps for the first 400 ms (`CONFIRM_ARM_DELAY_MS`, longer than the 300 ms double-tap timeout), so the second tap of a double tap can't confirm; no press-and-hold (slow, hidden, awkward with TalkBack). Destructive actions use the **warning button** (outline + ⚠ in the text colour, never cyan, never bust magenta); safe ones the cyan primary. "BUST" in a body is always magenta. Texts and "when" live in `Confirmations` (JVM-tested).

| Where | When it asks | Title · action | Style |
|---|---|---|---|
| Player editor → Delete | always | "Delete Anna?" · Delete → sheet slides down → list | destructive |
| Player sheet → Reset last entry | player is scored or bust | "Reset the last entry for Anna?" · Reset (body: removes +46 / the BUST) | destructive |
| Round → Close round | someone is open (nobody open: closes at once) | "Close round N?" + chips + "… marked BUST." · Close round | primary |
| Close round, nobody scored | all players open | "Nobody has been scored yet – mark everyone BUST?" · Close round | destructive |
| Round menu → End game | always | "End the game now?" · End game | destructive |
| Round menu → Players → untick a seated player | always | "Remove Anna from this game?" · Remove (body: "Their points from past rounds stay in the scoreboard." if they played a closed round, plus "Their +46 / BUST in this round is dropped." if they have a result) | destructive |
| Round menu → Target points | lowering it to ≤ the leader's total (once: not again when the leader already reached the old target) | "Lower the target to 150?" · Lower (body: the game ends after this round) | destructive |
| Seats → Start game | a game is unfinished | "Start a new game?" · Start (the unfinished game ends) | destructive |
| Home → long-press a game → Delete game | always (no Undo) | "Delete this game?" · Delete (body: "Today, 21:40 · Mia won · 6 rounds", then "This can't be undone."; the running game adds "The game in progress will be lost.") | destructive |

No confirmation, Undo instead: Bust (in-card, sheet, camera: Undo toast / overlay) and removing a total adjustment in the player sheet (inline "Removed +5 · Undo").

## Home (game history)
Home shows the **Resume card** of the unfinished game (round, avatars, leader, Resume) and below it **Past games**: every finished, ended-early and abandoned game, newest first (one `LazyColumn`). A row shows the date ("Today, 21:40" / "Yesterday, …" / "Sun 11 Oct" / "11 Oct 2025"), the winner(s) with the crown badge and their points, the players' avatars and the number of (closed) rounds, plus "ended early" / "not finished". Abandoned games are muted and say who led ("Lena led", or "Not finished" without points). Tap a row → that game's **Scoreboard**; finished games stay editable there, abandoned ones are read-only ("Not finished · 2 rounds · to 200", no running row). **Long-press** a row or the Resume card (haptic) → a small menu at the row (M3 `DropdownMenu`, restyled): **Open scoreboard** / **Resume**, and **Delete game** (warning style) → confirmation (see Confirmations) → the game and everything of it (seats, rounds, results, cards, adjustments) is deleted, the row animates out; players stay. Tapping the Resume card anywhere resumes. TalkBack: each row / the card has the custom action "Delete game" (a long-press isn't discoverable there). **Players** + **New game** stay pinned at the bottom; the intro text only shows when there is no game at all. The **⋮** button top right (TalkBack "More options") opens a small menu: the version as a muted first line ("Scan7 1.0 (1)"), **About & licenses** and **Language**. About & licenses shows version + model, the Flip 7 disclaimer, the AGPL-3.0 notice with the license text, links to the source code and the privacy policy, and the third-party notices (each opens its bundled license text). Later: stats.
**Language** in that menu is an inline radio group: System default / English / Deutsch (the same setting as Android's per-app language on API 33+). German says **RAUS** for BUST; dates and times follow the locale. The model details (name, fp32, input size, date, size) are only on the About screen and in the debug menu.

## Debug menu
**Debug builds only** (`BuildConfig.DEBUG`): tap the Scan7 wordmark on Home 5 times (a toast counts down after the 3rd tap; TalkBack action "Open debug menu"). "Save scans for training" lives there and is ON by default in debug builds; release builds have no debug menu and never save training scans.

## Camera
Shutter only; best frame of the last second is used. Torch toggle next to the zoom pills (off by default; shows the camera's real torch state).

## Stats
Store everything needed for statistics later (games, rounds, per-player round results incl. bust, and the confirmed cards per row), but no stats UI for now.

## Changes 2026-10-18 (evening)
- **Round end in one tap:** when everyone is done, **Next round** closes the round and starts the next one, without a toast (the results overlay and the later "Round N closed" snackbar were removed). Game over still goes to the Game over screen. (Replaces the separate end-of-round step.)
- **Review OK is a split button:** "OK" with the second line "Next: Ben" / "Back to table" (TalkBack "OK, then next: Ben"); manual Save / Confirm bust the same. The ▾ menu only **selects** (radio items with a check on the current one, "… selected"): it stores the `after_scan` setting, the second line follows, nothing is saved until OK is tapped. Same setting as the round menu's "After a score". Applies to every scan (card Scan button or Scan next).
- **Scan direction** ↻ clockwise / ↺ counter-clockwise in the round menu, remembered; only decides who "next" is.
- **Camera:** top row: ← back (= back to table), then the player banner; bottom: BUST · shutter · Type, with the zoom pills + torch floating above. No REC marker, no hint text. Bust shows a full-screen magenta "✕ <name> BUST" overlay (~1 s, with Undo) and blocks a second tap.
- **Review / manual chips:** no "ignored" text; action cards and backs only dashed outline. Stable panel height (no jump on bust), no "Misread a card…" text.
- **Compact cards (7–10 players):** name on one line, total below; double-tap a compact card to scan.

## Queued (after the avatar merge)
- Done: Home = game history (see "Home (game history)"), New game step 1 + Seats step (see "New game").
