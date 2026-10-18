Paste this into Claude Code from the root of the flip7-scanner project:

---

I've added a design handoff in `design/`. Please read `design/DESIGN_HANDOFF.md` first, then `brand/BRAND.md` and `docs/SCREENS.md`.

`design/reference/RoundScreen.dc.html` is an HTML prototype of the main Round screen (table + list view, arrange mode, scan-next). It's a reference for exact sizes, colors and behaviour, not code to ship. The `*_draft.dc.html` files are older drafts of Camera and Review: keep their content and layout ideas, but restyle them with the tokens in the handoff.

Plan, then build in this order, showing me each step on my phone before moving on:
1. Theme: colors, Unbounded + DM Mono fonts, spacing/radius constants in Compose (no Material default look).
2. Shared components: PlayerToken, ScoreRing, StatusTag, PlayerCard (3 size classes), PlayerRow, bottom buttons.
3. Round screen: table view with free positions + arrange mode (long-press, wiggle, drag, Done), list view with drag-to-reorder, view toggle, progress strip, Scan next / Close round. Use fake data first.
4. Wire it to the real game state and the existing camera/review flow, including "Scan next" going straight to the next player's camera.
5. Restyle the remaining screens from docs/SCREENS.md with the same components.

Ask me before deviating from the handoff.
