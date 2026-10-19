# Scan7 app flow

> Agreed 2026-10-18. This file specifies the game **flow**. [../design/DESIGN_HANDOFF.md](../../design/DESIGN_HANDOFF.md)
> defines look, layout and interaction and wins where the two differ. The current debug
> screens (live camera, detect from photo) stay available for testing.

## Players
- The phone stores players locally, each with a name and an **avatar**: color, pattern and face from the avatar kit ([../design/avatars/AVATARS.md](../../design/avatars/AVATARS.md): 15 colors, 6 patterns including plain, 12 faces; never cyan or magenta). Players can be added, renamed and deleted at any time. A game has at most 10 players.
- The avatar replaces the old initial token everywhere (round cards inside the score ring, list rows, camera header, review/manual, sheets, scoreboard, game over, resume card, players list, new game). The name always stays visible next to it. TalkBack reads "Mia, sand avatar, grin face".
- The **avatar editor** (player sheet) shows a live preview (88 dp) and the name at the top, with shuffle in the header. Below come Face (3×4), Pattern (1×6) and Color (3×5). Save stays pinned at the bottom.
- **Colors may repeat within a game.** Colors used by other players of the current game (or the game being set up in New game) show a small dot so people can avoid duplicates. Nothing is blocked.
- A new player gets a random face and pattern and the first color nobody uses. In New game, "nobody" means none of the selected players; in the players list, no stored player. If every color is used, the player gets the first color.
- No groups.

## New game
1. Select who plays tonight from the stored players (checkboxes; the tick order is the seating order). A new player can be added right here and is saved to the list. Shared avatar colors don't block **Next** (the "Same color as …" check is gone). The **target** (default 200, steps of 10) is a compact row pinned above the button. At the bottom is one full-width **[Next]**. There is no "Skip seats", because arranging on the Seats step is optional.
2. **Seats.** This step shows the round screen's own table or list in arrange mode, with the same cards, drag rules and Table | List toggle, and seat numbers 1…n on the cards or rows. The header is ← · "Seats" · Table|List. At the bottom are the hint "Drag players to where they sit, or just start" (list: "Drag rows into seating order, or just start") and **Start game**. Start game works without moving anyone: it uses the tick order on the default ring, or the earlier game's spots. In the table, drop a card where the player sits, and the order is re-derived clockwise. In the list, drop a row on another to swap the two. If the same players played an earlier game, that game's order and spots apply. The positions set here become the game's table positions, and an app preference remembers the chosen view. Back and predictive back return to step 1 and keep the arrangement, as long as the same players stay picked. "New game with same players" opens directly on this step.
3. Start opens round 1.

The app persists games, rounds and scores, so an unfinished game survives closing the app and can be resumed.

## Round screen
The round screen shows all players in seating order for the whole round, each with
- name and total so far,
- this round's status: `open`, `✓ +46` (scored), or `✕ BUST` (magenta **and** the word BUST).

The header shows the round number and the current leader. The leader badge is a white circle with a bold three-point crown (rounded tips over a base band). The glyph is about 84 % of the badge, so it reads at 20 dp. At the bottom is **Close round**.

There are two views. **Table** places cards freely around the screen. You arrange them by long-press and drag, or with **Rearrange seats / order**, the first item of the round menu ⋮. The table header is logo · Table|List · ⋮. **List** shows rows in seating order. Both views share the seating order. The default is Table for up to 6 players and List for 7 or more, and the app remembers the choice. Card positions are saved per game.

The round menu is a bottom sheet (it scrolls when it doesn't fit) with an M3 list, no header and no supporting texts. Its actions come most used first, as `ListItem` rows with icons: **Scoreboard**, **Rearrange seats / order**, **Players** (see "Players during a game"), **Home**, a divider, and **End game** (warning style; its confirmation explains it). Below them, the **SETTINGS** keep the sheet open: **Target points** (− value +, steps of 10, 50–1000, this game only), **Scan direction** (M3 segmented buttons ↻ Clockwise | ↺ Counter), **After a score** (Next player | Back to table, the same `after_scan` setting as the review's split OK ▾) and **Entry** (Scan | Type, app setting `entry_mode`).

**Target points** changes the running game's target at once (the progress "to N", the scoreboard subtitle, Home). Lowering it to or below the leader's total asks first ("Lower the target to N? The game ends after this round.", destructive). The game-over check at the round close uses the new target. The setting is hidden once the game is over.

With **Entry: Type** (no camera), every scan entry point (in-card Scan, Scan next, sheet Scan, double-tap) opens the manual entry directly, with no camera and no camera permission prompt. Results are saved with source MANUAL. The in-card button shows a keypad icon, the bottom button says "ENTER NEXT · Ben" (DE "WEITER EINGEBEN"), and the player sheet's big button is "Enter manually". Done players have no Rescan, because Edit covers it. The Scan next chain continues with the next player's manual entry, following "After a score".

Open cards have in-card **Scan** and **Bust** buttons. Bust marks the player busted immediately and shows an "Undo" toast. Tapping an open card opens a sheet with **Scan**, **Enter manually** and **Bust**. Tapping a done card shows its result with **Edit**, **Rescan** and **Reset last entry** (back to open). Compact cards (7–10 players) show the name on one line and the total below. **Double-tap** an open card to scan it (TalkBack action "Scan <name>").

At the bottom are **Scan next · <next open player>** (primary) and **Close round**. When everyone is done, one full-width **Next round** closes the round and starts the next one in one tap. "Scan next" follows the **scan direction** (round menu ⋮: ↻ clockwise / ↺ counter-clockwise, app setting `scan_direction`). Seating and list order don't change.

## Players during a game
**Players** in the round menu (⋮) sits below Rearrange seats / order and is hidden once the game is over. It opens New game's step-1 picker (same rows, checkboxes and "+ New player") with the header "Players · Round 6 · 7 of 10", preselected with the seated players. Rows that change say "Joins in round 6, open" or "Leaves the game". **Done** with a changed lineup opens **Seats** (the round table or list in arrange mode, with Done instead of Start game) and then returns to the round in one transaction (`GameState.withLineup`). An unchanged lineup goes straight back. Back leaves without changes.
- **Adding.** The player joins the running round as **open** with total 0. Their earlier rounds have no result and show "–" in the scoreboard (header tag "FROM R6", TalkBack "joined in round 6"). The default seat is after the last seat, between the last and the first card. If the table is still on the default ring, everyone moves to the ring of the new size. A player who had left comes back with their old results, and the gap rounds show "–".
- **Removing.** Unticking a seated player asks first (warning style, see Confirmations). The player leaves the running round, and their result there (points or BUST) is dropped. Past results and adjustments stay and count in their total. Their scoreboard column moves to the end, dimmed, with the tag "LEFT". They no longer count for "all done", Scan next, the progress strip, leaders or the winner. The game-over check looks at seated players only, and Game over lists the players who left last as "left", unranked. Someone who joined this round and has nothing recorded just disappears.
- A game has 2 to 10 players (`MIN_PLAYERS` … `MAX_PLAYERS`). The lineup can change only while a round is running.
- The seat row stores `game_players.joinedRound` (default 1) and `leftRound` (null while playing), DB v5 (AutoMigration from 4 to 5). Home history and the scoreboard show everyone who played (seated players first, then the ones who left) with their totals. "Same players" (New game, previous seats) means the final seated lineup.

## Scan
1. A full-screen camera shows the player name at the top and live boxes on the cards.
2. **Shutter button only** (no auto-capture). Pressing it freezes the current frame.
   Left of the shutter, **Bust** (magenta outline and word) marks the player busted without scanning. The full-screen bust overlay (about 1 s, with Undo) confirms it, and the flow then continues like a save, with no toast afterwards. Only Back during the overlay leaves the bust with the Undo toast on the round screen. Right of the shutter is **Type** (manual entry). The top row is ← back, then the player: avatar and "SCANNING" / "NEXT" · total over the name in Unbounded ~28 sp. Tap the preview to focus there. Pinch to zoom, and the zoom pills follow. Zoom pills appear only for what the camera supports (CameraX `ZoomState`): 0.5× when the min zoom is ≤ 0.6 (ultra-wide), then 1×, 2× and 5× up to the max zoom. There is no pill row when the max zoom is below 1.5× or only one pill is left. A remembered zoom the camera can't do falls back to 1×.
3. The review overlay sits on the frozen frame. Boxes and labels stay on the cards, and a bottom panel shows
   - detected cards as chips,
   - the score breakdown (numbers, x2, plus cards, Flip 7 bonus) and the total,
   - the buttons **Retake** (back to the camera), **Fix** (the manual screen, prefilled) and **OK** (save; a split button, see below).
   - Tapping a box or its chip **leaves that card out**, for example a neighbour's card in the frame. The box turns dashed grey with "EXCLUDED" and the chip is struck through. Score, bust check and the saved result ignore it. Tap again to include it. Fix doesn't prefill left-out cards.
   After OK, a scan started via **Scan next** goes straight to the camera for the next open player ("Next: Ben", with "Back to table"). Any other scan returns to the round screen.
4. If a number is detected twice, the panel shows **BUST · two 7s** (magenta and text) with **Confirm bust** and **Fix**.
   **Second Chance rule** (in `scoreRow`). Each `second_chance` card in the row that is not left out cancels one extra copy of a number. If every extra copy is covered, there is no bust, the duplicated number counts once (also for Flip 7), and review and manual show the cyan line "Second Chance used · 7 counted once".
5. **Leave a card out.** Tapping a box on the photo or its chip toggles it *excluded* (dashed grey, "EXCLUDED"), for example a neighbour's card. Excluded cards don't count for the score or the bust check, aren't part of the saved result and aren't prefilled in Fix. The training file (debug builds with "Save scans for training" on) still keeps them, marked `"excluded": true`, because they are real cards the detector should learn.

## Manual screen
The manual screen serves **Enter manually**, **Fix** (prefilled with the scanned cards) and **Edit**.
- Top: the selected cards as chips. Tap a chip to remove it.
- Grid: `0`–`12`, `+2` `+4` `+6` `+8` `+10` `x2`. Tapping adds a card once or removes one copy. The grid never adds a second copy, but a duplicate prefilled by Fix shows "×2" (magenta while it busts), and each tap removes one copy, so the user can keep one 12.
- **At most 7 number cards** (duplicates count). Once 7 are selected, the other number buttons are disabled ("7 number cards – the maximum"). The review blocks OK with a warning if a scan includes more than 7, and the user leaves cards out or uses Fix.
- Action cards and `back` are not in the grid, because they never score. If the scan found them, they show as grey "ignored" chips.
- Bottom: the live score breakdown and **Save**.

## Close round
1. A confirmation appears only while someone is open: "X and Y haven't been scored. They'll be marked BUST." Confirming makes every open player busted (0 points). If nobody has been scored at all, it asks "Nobody has been scored yet – mark everyone BUST?" in destructive style, since that is likely an accident.
2. The round closes and the next one starts at once, in one transaction. There is no toast, because the scoreboard is in the round menu.
3. If at least one player has reached the target (≥ 200) **after the full round is scored**, the game ends. The player with the most points wins, and an exact tie is a shared win. Reaching 200 mid-round never ends the round early.
4. Otherwise the next round starts with all players `open`.

Closed rounds can be reopened from the scoreboard and edited. The app then recomputes the totals and the game-over state.

Results of closed rounds are edited from the scoreboard. The game-over check runs when the running round closes.

## Adjust total
**Adjust total** in the player sheet (any state, also on the end-of-round view) opens a small dialog with a signed number. The number comes from a stepper (−10 −5 −1 +1 +5 +10), a ± sign toggle, or typed input (`+10`, `-12`; max ±999). The dialog also takes an optional note ("penalty", "house rule") and shows a preview "Total 123 → 133".
- Each adjustment is stored as its own entry (table `score_adjustments`, DB v3), not as a round result, and records the round that was running.
- The sheet lists the player's adjustments (delta, note, round), each with ✕ to remove it (inline **Undo**).
- All totals include adjustments via `GameState.totals`: round cards and rows, leader, progress, the scoreboard TOTAL row, game over and the resume card. There is no separate scoreboard row. An adjusted total shows a tiny "±".
- Game over is unchanged. It is checked only once the round is closed, so an adjustment mid-round never ends the game. On the end-of-round view, totals are re-checked like any edit.

## End game early
**End game** in the round menu (⋮) asks for confirmation (see Confirmations). The app then closes the current round as it is, with open players marked BUST, and shows Game over. The current leader or leaders win, and a tie is a shared win.

## Confirmations
Every confirmation uses one pattern: `Scan7ConfirmDialog` (ui/components, built on `Scan7Dialog` = M3 `BasicAlertDialog`), centred over a dim scrim and **never inline in a sheet**, so the sheet underneath keeps its layout. It has a title such as "Delete Anna?", an optional one-line body and a fixed row **[Cancel] [Action]** (1 : 1.4). Back or a scrim tap means Cancel. For TalkBack, the pane title is the title and focus starts on the title. Confirm ignores taps for the first 400 ms (`CONFIRM_ARM_DELAY_MS`, longer than the 300 ms double-tap timeout), so the second tap of a double tap can't confirm. There is no press-and-hold, because it is slow, hidden and awkward with TalkBack. Destructive actions use the **warning button** (outline and ⚠ in the text colour, never cyan, never bust magenta). Safe ones use the cyan primary. "BUST" in a body is always magenta. The texts and the "when" rules live in `Confirmations` (JVM-tested).

| Where | When it asks | Title · action | Style |
|---|---|---|---|
| Player editor → Delete | always | "Delete Anna?" · Delete, then the sheet slides down to the list | destructive |
| Player sheet → Reset last entry | player is scored or bust | "Reset the last entry for Anna?" · Reset (body: removes +46 / the BUST) | destructive |
| Round → Close round | someone is open (nobody open: it closes at once) | "Close round N?" with chips and "… marked BUST." · Close round | primary |
| Close round, nobody scored | all players open | "Nobody has been scored yet – mark everyone BUST?" · Close round | destructive |
| Round menu → End game | always | "End the game now?" · End game | destructive |
| Round menu → Players → untick a seated player | always | "Remove Anna from this game?" · Remove (body: "Their points from past rounds stay in the scoreboard." if they played a closed round, plus "Their +46 / BUST in this round is dropped." if they have a result) | destructive |
| Round menu → Target points | lowering it to or below the leader's total (asked once, not again when the leader already reached the old target) | "Lower the target to 150?" · Lower (body: the game ends after this round) | destructive |
| Seats → Start game | a game is unfinished | "Start a new game?" · Start (the unfinished game ends) | destructive |
| Home → long-press a game → Delete game | always (no Undo) | "Delete this game?" · Delete (body: "Today, 21:40 · Mia won · 6 rounds", then "This can't be undone."; the running game adds "The game in progress will be lost.") | destructive |

Two actions use Undo instead of a confirmation: Bust (in-card, sheet and camera, with an Undo toast or overlay) and removing a total adjustment in the player sheet (inline "Removed +5 · Undo").

## Home (game history)
Home shows the **Resume card** of the unfinished game (round, avatars, leader, Resume) and below it **Past games**: every finished, ended-early and abandoned game, newest first (one `LazyColumn`). A row shows the date ("Today, 21:40" / "Yesterday, …" / "Sun 11 Oct" / "11 Oct 2025"), the winner or winners with the crown badge and their points, the players' avatars and the number of closed rounds, plus "ended early" or "not finished". Abandoned games are muted and say who led ("Lena led", or "Not finished" without points).

Tapping a row opens that game's **Scoreboard**. Finished games stay editable there. Abandoned ones are read-only ("Not finished · 2 rounds · to 200", no running row). A **long-press** on a row or the Resume card (with haptic feedback) opens a small menu at the row (M3 `DropdownMenu`, restyled) with **Open scoreboard** or **Resume**, and **Delete game** (warning style). Delete game asks for confirmation (see Confirmations) and then deletes the game and everything that belongs to it (seats, rounds, results, cards, adjustments). The row animates out, and the players stay. Tapping the Resume card anywhere resumes the game. For TalkBack, each row and the card have the custom action "Delete game", because a long-press isn't discoverable there.

**Players** and **New game** stay pinned at the bottom. The intro text only shows when there is no game at all. The **⋮** button top right (TalkBack "More options") opens a small menu: the version as a muted first line ("Scan7 1.0 (1)"), **About & licenses** and **Language**. About & licenses shows the version and model, the Flip 7 disclaimer, the AGPL-3.0 notice with the license text, links to the source code and the privacy policy, and the third-party notices (each opens its bundled license text). Stats come later.

**Language** in that menu is an inline radio group with System default / English / Deutsch (the same setting as Android's per-app language on API 33+). German says **RAUS** for BUST, and dates and times follow the locale. The model details (name, fp32, input size, date, size) appear only on the About screen and in the debug menu.

## Debug menu
The debug menu exists in **debug builds only** (`BuildConfig.DEBUG`). Tap the Scan7 wordmark on Home 5 times; a toast counts down after the 3rd tap, and TalkBack has the action "Open debug menu". "Save scans for training" lives there and is ON by default in debug builds. Release builds have no debug menu and never save training scans.

## Camera
Shutter only. The app uses the best frame of the last second. A torch toggle sits next to the zoom pills. It is off by default and shows the camera's real torch state.

## Stats
Store everything needed for statistics later (games, rounds, per-player round results including bust, and the confirmed cards per row), but build no stats UI for now.

## Changes 2026-10-18 (evening)
- **Round end in one tap.** When everyone is done, **Next round** closes the round and starts the next one, without a toast. The results overlay and the later "Round N closed" snackbar were removed. Game over still goes to the Game over screen. This replaces the separate end-of-round step.
- **Review OK is a split button.** "OK" has the second line "Next: Ben" / "Back to table" (TalkBack "OK, then next: Ben"), and manual Save / Confirm bust work the same way. The ▾ menu only **selects** (radio items with a check on the current one, "… selected"). It stores the `after_scan` setting and the second line follows, but nothing is saved until OK is tapped. It is the same setting as the round menu's "After a score" and applies to every scan (card Scan button or Scan next).
- **Scan direction** ↻ clockwise / ↺ counter-clockwise in the round menu is remembered and only decides who "next" is.
- **Camera.** The top row has ← back (back to the table), then the player banner. The bottom row has BUST · shutter · Type, with the zoom pills and torch floating above. There is no REC marker and no hint text. Bust shows a full-screen magenta "✕ <name> BUST" overlay (about 1 s, with Undo) and blocks a second tap.
- **Review and manual chips.** There is no "ignored" text; action cards and backs only get a dashed outline. The panel height is stable (no jump on bust), and the "Misread a card…" text is gone.
- **Compact cards (7–10 players).** The name sits on one line and the total below. Double-tap a compact card to scan.

## Queued (after the avatar merge)
- Done: Home as game history (see "Home (game history)"), New game step 1 and the Seats step (see "New game").
