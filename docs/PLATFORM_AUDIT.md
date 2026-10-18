# Platform audit: hand-built vs. platform (Android / Jetpack / Material 3)

Date: 2026-10-18 · Scope: `app/app/src/main` (all packages, manifest, res, build files) · Read-only audit, no code changed.
Rule applied: AGENTS.md "Platform first". Design constraint: custom Scan7 look, no Material default look (DESIGN_HANDOFF.md §1). A native component is fine if it is restyled with the Scan7 tokens.
Paths are relative to `app/app/src/main/java/net/moesli/scan7/` unless they start with `app/` or `res/`. Line numbers are from the main checkout. `ui/round` and `ui/scan` are being edited in worktrees right now, so their line numbers will shift.

Priority: **P1** = user-visible behaviour or accessibility · **P2** = behaviour gap or robustness · **P3** = maintenance only.
Effort: **S** < ½ day · **M** ½–2 days · **L** > 2 days.

## Summary

| # | Area | What is custom | Native alternative | Prio | Effort | Recommendation | Batch |
|---|---|---|---|---|---|---|---|
| 1 | Undo toast | `BustToast` + `BustToastState` + timeout code written twice (round, camera) | M3 `SnackbarHost` / `SnackbarHostState` with a custom `snackbar` slot | P1 | S–M | **Replace now** (once the round/scan worktrees are merged) | B |
| 2 | Checkbox rows | Row with `role = Checkbox` + hand-written "Selected" state; drawn checkbox | `Modifier.toggleable(role = Checkbox)` on the row (box can stay custom) | P1 | S | **Replace now** | C |
| 3 | Theme for native components | No `shapes`, ripple still on inside M3 components, `PressDim` set per call | `MaterialTheme(shapes = …)`, `LocalRippleConfiguration provides null`, `LocalIndication provides PressDim` | P1 | S | **Replace now** (needed before more M3 components come in) | A |
| 4 | Predictive back | No `enableOnBackInvokedCallback`; no in-app back preview | Manifest flag (API 33–35 devices); in-app animation from Navigation 3 (#5) | P1 | S | **Replace now** (flag) | A |
| 5 | Navigation | Self-made back stack (`ui/nav`), string encoding, no transitions, no ViewModel scope | **Navigation 3** (`NavDisplay`, `rememberNavBackStack`, entry decorators) | P2 | M | **Replace later: needs your decision** (it changes a logged decision) | D |
| 6 | Split OK ▾ menu (in progress) | `Popup` + hand-drawn menu rows | M3 `DropdownMenu` / `DropdownMenuItem` restyled (maybe `SplitButtonLayout`) | P1 | S | **Replace now** (tell the ui/scan agent, or right after merge) | B |
| 7 | Torch | Local `torchOn` flag, never read back from the camera | `cameraInfo.torchState` as the source of truth | P1 | S | **Replace now** | B |
| 8 | Haptics | Only `ContextClick` / `GestureEnd`; some gestures have none | Semantic `HapticFeedbackType` (Confirm, ToggleOn/Off, SegmentTick, GestureThresholdActivate …) | P2 | S | **Replace now** | B, C |
| 9 | Keep screen on | `view.keepScreenOn` toggled in a `DisposableEffect` | `Modifier.keepScreenOn()` (Compose UI 1.9+, reference-counted) | P2 | S | **Replace now** | B |
| 10 | Tap-to-focus / pinch / exposure | Not there (zoom presets only) | `CameraXViewfinder` + `MutableCoordinateTransformer` → `FocusMeteringAction`; `transformable` → `setZoomRatio`; `setExposureCompensationIndex` | P2 | S–M | **Replace later** (tap-to-focus first) | B |
| 11 | Seating reorder | Platform drag detectors, but scrolling is off while dragging, no auto-scroll, rows jump | `LazyColumn` + `Modifier.animateItem()` + Calvin-LL `Reorderable` (auto-scroll) | P2 | M | **Replace later** | C |
| 12 | Round arrange drag | Custom `awaitEachGesture` (long-press → drag, free 2D positions) | none that fits (see details) | — | — | **Keep**; add auto-scroll in the list view, respect "Remove animations" | B |
| 13 | DB writes in composition scope | `rememberCoroutineScope()` for repository writes; no ViewModels | `ViewModel` + `viewModelScope` (scoped per entry with Navigation 3), or an app-wide scope | P2 | S (app scope) / M (ViewModels) | **Replace later** (quick fix now: app scope) | D (C for quick fix) |
| 14 | Strings / l10n | Every UI string in Kotlin; plurals, ordinals and name lists built by hand | `strings.xml`, `pluralStringResource`, ICU `MessageFormat {0,ordinal}`, `ListFormatter` | P2 | L | **Replace later**, package by package | A, B, C |
| 15 | Font scaling | Fixed heights with `sp` text, 8–9 sp labels | `heightIn(min)`, `@PreviewFontScale`, a cap only where geometry needs it | P1 | M | **Audit later** | A, B, C |
| 16 | Icons / RTL | 3 Canvas icon helpers, 2 different `BackIcon`s, no mirroring | `ImageVector` (`autoMirror = true`) + M3 `Icon` | P2 | S–M | **Replace later** | A (+B for ScanParts) |
| 17 | Edge-to-edge | Deprecated bar colours in themes.xml + `SideEffect` for icon appearance | `enableEdgeToEdge(SystemBarStyle.dark(…))` | P3 | S | **Replace now** | A |
| 18 | Insets helpers | `composed {}` + manual `statusBars ∪ 48dp` | Plain `windowInsetsPadding`, `safeDrawing` (cutouts) | P3 | S | **Replace later** | A |
| 19 | Dialog | `Scan7Dialog` on `ui.window.Dialog` (good), no pane title, no exit animation | `BasicAlertDialog` (restyled) or `semantics { paneTitle }` | P2 | S | **Keep** the wrapper, add the pane title | A |
| 20 | Preview sheet panel | `BottomSheetPanel`: fake grab handle + empty `clickable` (adds a click action for TalkBack) | Preview the content in a plain `Column`; share one `SheetGrabHandle` | P3 | S | **Replace now** (only previews and the gallery use it) | A |
| 21 | Settings storage | Room `settings` table + a separate SharedPreferences file for zoom | Keep Room (transactional), move zoom into it; DataStore only if settings grow | P3 | S | **Replace later** | after B+C |
| 22 | Training JSON | Hand-built JSON + `SimpleDateFormat` | kotlinx-serialization-json (`@Serializable`, `@SerialName`) + `java.time` | P3 | S–M | **Replace later** | C |
| 23 | Text fields | `BasicTextField(value, onValueChange)`; focus not shown; label as contentDescription | `BasicTextField(TextFieldState)` + `InputTransformation`; `collectIsFocusedAsState` | P2 | S | **Replace later** | A, C |
| 24 | Steppers | Target stepper = 3 TalkBack stops | `semantics { progressBarRangeInfo; setProgress }` (adjustable) | P2 | S | **Replace later** | C |
| 25 | Scoreboard grid | No table semantics | `collectionInfo` / `collectionItemInfo` | P2 | S | **Replace later** | C |
| 26 | Splash | Android 12+ attribute only; blank `Loading()` boxes | `core-splashscreen`: `installSplashScreen()` + `setKeepOnScreenCondition` | P3 | S | **Replace later** | A |
| 27 | Segmented toggles | `ViewToggle`, `ZoomPills` | M3 `SegmentedButton` | — | — | **Keep** (pill-in-pill look; semantics already right) | — |
| 28 | Buttons, chips, grid toggles | Box + `clickable` / `toggleable` / `selectable` | M3 `Button` / `FilterChip` | — | — | **Keep** (card token look; foundation semantics are right) | — |
| 29 | Permissions, photo picker, MediaStore, detector thread | Activity Result API, `PickVisualMedia`, `IS_PENDING`, single-thread executor | (already platform) | — | — | **Keep** | — |
| 30 | Misc | Debug switch drawn by hand; editor state not saveable; backup XML templates; no baseline profile, shortcuts or share | M3 `Switch`; `rememberSaveable`; explicit backup rules; Baseline Profile; `ShortcutManagerCompat`; `ShareCompat` | P3 | S each | **Later / optional** (shortcuts and share are product decisions) | various |

---

## Details

### 1. Bust undo toast → `SnackbarHost` (P1, S–M, replace now)
- **Custom:** `ui/round/RoundScreen.kt:123-147` (`BustToastState`), `:239-262` (timeout + placement), `ui/round/RoundParts.kt:165-208` (`BustToast`), and again `ui/scan/ScanCamera.kt:303-332` (`CameraBustToast`, a second copy of the timeout logic and the `BUST_TOAST_MS` constant). Shared state is created in `ui/nav/AppNavHost.kt:121`.
- **Native:** `SnackbarHostState.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)` returns `SnackbarResult.ActionPerformed` / `Dismissed`. Keep the look by passing `snackbar = { data -> BustToast(data) }` to `SnackbarHost`.
- **Benefits:**
  - The host already uses `AccessibilityManager.calculateRecommendedTimeoutMillis`, so we can delete our two copies of that logic.
  - It adds the live-region announcement plus a TalkBack **dismiss** action.
  - `showSnackbar` is a suspend call, so the undo flow becomes linear: `if (result == ActionPerformed) undoBust(id)`.
  - Optional swipe to dismiss with `SwipeToDismissBox`.
  - One place for timing.
- **Behaviour to keep:** a new bust replaces the current toast. Call `currentSnackbarData?.dismiss()` before `showSnackbar` (the default host queues). Keep the `BustToastState` API (`show`/`dismiss`/`current`) as a thin wrapper over `SnackbarHostState` so `AppNavHost` and the camera don't change. That keeps this item out of the nav package.
- **Also:** the "Removed +10 · Undo" line in `ui/sheet/PlayerSheet.kt:168-183` sits inside the sheet. **Keep** it inline; a snackbar behind a modal sheet would be hidden.
- **Risk:** low. The host's own enter/exit animation differs slightly; restyle through the slot.

### 2. Checkbox rows without toggle semantics (P1, S, replace now)
- **Custom:** `ui/newgame/NewGameScreens.kt:145-156` (`SetupPlayerRow(role = Role.Checkbox)` + `stateDescription = "Selected"/"Not selected"`), `:187-202` (drawn checkbox), and `ui/players/PlayersScreen.kt:149` (`scan7Clickable(onClick, enabled, role, …)`).
- **Problem:** a clickable with `Role.Checkbox` but no `ToggleableState`. TalkBack doesn't say "checked / not checked" in the system wording (or language), and the state text is hard-coded English.
- **Native:** `Modifier.toggleable(value = checked, enabled, role = Role.Checkbox, indication = PressDim, onValueChange = …)` on the row; drop the manual `stateDescription`. The drawn 24 dp box can stay (or M3 `Checkbox` with `CheckboxDefaults.colors(...)` from the tokens, with `onCheckedChange = null` so the row owns the click).
- **Risk:** none.

### 3. Theme defaults for native components (P1, S, replace now)
- **Custom:** `ui/theme/Theme.kt:57-78` sets only `colorScheme` + `typography`. `PressDim` is passed by hand to every `clickable` (`ui/components/Buttons.kt:47-59`).
- **Why it matters now:** M3 components keep using `ripple()` and default `Shapes` internally. That covers the new `ModalBottomSheet`, and next the snackbar (#1), `DropdownMenu` (#6), `BasicAlertDialog` (#19) and `DropdownMenuItem`. That brings back the "Material default look" the handoff forbids.
- **Native:**
  - `MaterialTheme(shapes = Shapes(extraSmall = 6.dp, small = 8.dp, medium = 12.dp, large = 18.dp, extraLarge = 18.dp))` mapped to `Scan7Radii`.
  - `CompositionLocalProvider(LocalRippleConfiguration provides null, LocalIndication provides PressDim)`. Plain `Modifier.clickable` then gets the Scan7 press dim by default, and `scan7Clickable` becomes a one-liner.
- **Risk:** low. Check the debug screens that use Material buttons on purpose.

### 4. Predictive back (P1, S, replace now: manifest flag)
- **Custom / missing:** `app/app/src/main/AndroidManifest.xml:12` `<application>` has no `android:enableOnBackInvokedCallback="true"`. With `targetSdk = 37`, predictive back is the default on Android 16+. On API 33–35 devices (inside `minSdk 29`) the system back-to-home animation needs the flag. Every back path already goes through Compose `BackHandler` (no `onBackPressed` overrides), so the flag is safe.
- **In-app predictive back** (the previous screen peeks in while the user swipes): not possible with the self-made stack, which swaps screens instantly with no transition (`AppNavHost.kt:123-126`). Navigation 3's `NavDisplay` gives this (#5). Building it ourselves with `PredictiveBackHandler` + `SeekableTransitionState` would reinvent Navigation 3. Not recommended.
- Inner back handlers (arrange mode `RoundScreen.kt:171`, scan steps `ScanFlow.kt:133/172/189`, new-game step `NewGameScreens.kt:469`) work with predictive back as they are (no animation, which is fine for in-screen state).

### 5. Navigation: self-made back stack vs. Navigation 3 (P2, M, needs a decision)
**What we have** (`ui/nav/AppNavHost.kt:52-127`, `ui/nav/Routes.kt:32-119`): `BackStack(List<Route>)` saved with `rememberSaveable` as strings (`encode`/`decodeRoute`, 50 lines plus tests), a `SaveableStateHolder` per entry, `BackHandler` pops, and `Stack.push/pop/replaceTop/reset` as pure functions (JVM tested).

| Concern | Self-made today | Navigation 3 | Navigation Compose 2.9 |
|---|---|---|---|
| Back stack model | `List<Route>` we own | `SnapshotStateList<NavKey>` we own (**same model**) | Owned by `NavController` (popUpTo etc.) |
| Process death / rotation | Yes (string list) | Yes (`rememberNavBackStack`, kotlinx.serialization) | Yes |
| Per-entry saved UI state | Yes (manual holder + cleanup) | Yes (`rememberSaveableStateHolderNavEntryDecorator`) | Yes |
| Per-entry ViewModel | **No** | Yes (`rememberViewModelStoreNavEntryDecorator`) | Yes |
| Screen transitions | **None** | Yes (`transitionSpec`, `popTransitionSpec`) | Yes |
| In-app predictive back animation | **No** | Yes (`predictivePopTransitionSpec`) | Yes |
| Deep links | Debug intents only (build initial stack) | None built in (map intent → initial stack, as today) | `deepLinks` in the graph |
| Typed routes | Hand-rolled encode/decode | `@Serializable` keys | `@Serializable` routes |

**Recommendation:** Navigation 3. It matches what we already built: our `Stack` helpers become plain list operations on the Nav3 back stack and keep their unit tests. In exchange we get transitions, predictive back, ViewModel scoping (#13), and we can delete the string encoder.
- **Effort:** M (about 1 day incl. test pass). Rewrite `AppNavHost`, turn `Route` into `NavKey` with `@Serializable` (needs the kotlinx-serialization plugin, which also serves #22), and adapt `RoutesTest` / `ScanNextBustTest`.
- **Risks:** medium.
  1. The "Scan next" chain relies on one shared state key (`Routes.kt:64-68`) so the camera stays bound while the player changes. In Nav3, `replaceTop` with a new key recreates the entry. Model the chain as one entry whose current player lives in its state, or give the entries a shared content key. Otherwise the camera rebinds and flickers between players.
  2. `GameOverRoute`'s reset-on-back (`AppNavHost.kt:198`) and the `LaunchedEffect` replace (`:202`) must keep working.
  3. Do it alone, after the other batches are merged: it touches every route's call site.
- **If you decide to stay self-made:** add the manifest flag (#4) and accept "no transitions, no in-app back preview, no ViewModels". Don't hand-build the animations.
- This changes the AGENTS.md decision "small self-made back stack … no navigation-compose" (2026-10-18), so it needs your OK.

### 6. Split OK button ▾ menu (in progress in the ui/scan worktree) (P1, S, replace now)
- **Custom:** `.claude/worktrees/agent-a3e52aa5214405917/app/app/src/main/java/net/moesli/scan7/ui/scan/ScanControls.kt:184-250`. `AfterScanMenu` is a raw `Popup(offset = -anchorHeight)` with hand-made rows. The rows use `semantics { selected }` without a role or `selectableGroup`.
- **Native:**
  - M3 `DropdownMenu(expanded, onDismissRequest, shape, containerColor = surface, border = BorderStroke(1.5.dp, line))` + `DropdownMenuItem(colors = MenuDefaults.itemColors(...))`.
  - Items as `Modifier.selectable(role = Role.RadioButton)` inside a `selectableGroup()`.
  - Optionally base the button on M3 `SplitButtonLayout` (material3 1.4+, experimental; check the BOM).
- **Benefits:** the menu opens upward or downward depending on space (today the offset is hard-coded), enter/exit animation, keyboard and focus handling, menu semantics, Back and outside-tap behave like a system menu. With #3 the items get the Scan7 press dim, not a ripple.
- **Split button semantics** (main part "OK, then Next: Ben" plus "Choose what happens after OK") are good. Keep them.

### 7. Torch state (P1, S, replace now)
- **Custom:** `ui/scan/ScanCamera.kt:107-109` keeps `torchOn` locally. `camera/CameraPreview.kt:151-158` only calls `enableTorch(torchOn)` when `camera` or `torchOn` changes.
- **Problem (to verify on the phone):** after the app goes to the background and comes back, CameraX reopens the camera with the torch **off** (same `Camera` object, so the effect doesn't run again), but the toggle still shows "on". The same happens if the system turns the torch off (overheating, another app).
- **Native:** observe `camera.cameraInfo.torchState` (LiveData → `asFlow()` / `observeAsState()`) as the shown state; the toggle only calls `enableTorch`. Restore the user's choice in a `LifecycleResumeEffect` if wanted.

### 8. Haptics (P2, S, replace now)
- **Custom:**
  - `ContextClick` for Bust (`RoundScreen.kt:167`, `RoundRoute.kt:248`, `ScanCamera.kt:196`) and for the shutter (`ScanCamera.kt:182`).
  - `GestureEnd` on drop (`RoundScreen.kt:363,509`).
  - None for the torch / view toggle, starting a drag in arrange mode (no long press there), a row crossing a slot, or the seating drag (`NewGameScreens.kt:288-309`).
- **Native:** Compose UI 1.8+ `HapticFeedbackType` semantic constants:
  - `Confirm`: Bust, OK.
  - `ToggleOn` / `ToggleOff`: torch, view toggle, grid toggles.
  - `GestureThresholdActivate`: drag start without a long press.
  - `SegmentTick`: crossing a slot in the list / seating drag.
  - `LongPress`: already played by `combinedClickable`.
- The system maps these to the device's tuned effects and respects the user's haptics setting.

### 9. Keep screen on (P2, S, replace now)
- **Custom:** `camera/CameraPreview.kt:176-181` sets `view.keepScreenOn = true/false` in a `DisposableEffect`. When two previews overlap during a change, disposing one clears the flag for the other.
- **Native:** `Modifier.keepScreenOn()` (Compose UI 1.9+, reference-counted per window; check it is in BOM 2026.09) on the preview `Box`.
- **Decision for you:** should the Round screen keep the screen on during a game? The phone lies on the table between rounds.

### 10. Camera features we don't use (P2, S–M, replace later)
- **Tap-to-focus / metering:** `CameraPreview.kt:185-189` shows `CameraXViewfinder` without a `coordinateTransformer`. Add a `MutableCoordinateTransformer`, convert the tap in `detectTapGestures`, and run `SurfaceOrientedMeteringPointFactory` → `FocusMeteringAction` → `cameraControl.startFocusAndMetering`. It helps with glossy cards and close focus in dim rooms. Show a small cyan focus ring (Scan7 look).
- **Exposure:** `cameraControl.setExposureCompensationIndex` (range from `cameraInfo.exposureState`) for bright table tops. Later, maybe as an automatic fix.
- **Pinch zoom:** `Modifier.transformable` / `detectTransformGestures` → `setZoomRatio(zoomState.zoomRatio * zoomChange)`. Optional: the presets already switch lenses. If we add it, the presets must follow `ZoomState` (today `zoom` is our own saved float, `CameraPreview.kt:110,170-174`).
- **ZoomState observer:** `CameraPreview.kt:160-168` hand-wires `observe/removeObservers`. `LiveData.asFlow()` + `collectAsStateWithLifecycle`, or `observeAsState`, is shorter and lifecycle-safe (P3).

### 11. Seating order reorder (P2, M, replace later)
- **Current:** `ui/newgame/NewGameScreens.kt:211-333` already uses platform detectors (`detectDragGesturesAfterLongPress` on the row, `detectDragGestures` on the handle) and TalkBack move up/down actions (`:364-369`). Good.
- **Gaps:**
  - `verticalScroll(enabled = draggingId == null)` (`:260`) turns scrolling off while dragging and there is no auto-scroll. With 10 players (10 × 80 dp plus header and stepper) a row can't reach a slot off screen.
  - The other rows jump instead of animating.
  - No haptics.
- **Native:** `LazyColumn` + `Modifier.animateItem()`, plus Calvin-LL **Reorderable** (`sh.calvin.reorderable`, well maintained, Compose-only). It gives long-press or handle drag, edge auto-scroll, and hooks for haptics. Keep our custom actions.

### 12. Round screen arrange drag: keep (justified)
- **Custom:** `ui/round/ArrangeDrag.kt:57-104` (Initial-pass `awaitEachGesture`: long press 450 ms → arrange mode → drag goes on in the same gesture; in arrange mode the drag starts at once) and `RoundScreen.kt:314-562`.
- **Why the platform doesn't fit:**
  - `Modifier.dragAndDropSource/Target` moves *data* with a system drag shadow; the card itself can't follow the finger with its scale and shadow. It is meant for moving data between apps or areas.
  - `anchoredDraggable` snaps to fixed anchors, but the table needs free positions.
  - `detectDragGesturesAfterLongPress` / `detectDragGestures` each cover only one of the two entry modes, and the root-coordinate tracking (comment at `:52-56`) fixes a real drift.
- **Recommendation:** keep it, and add the one-line "why custom" comment the rule asks for (the KDoc nearly says it). Two follow-ups:
  - The list view's `verticalScroll` (`RoundScreen.kt:442`) has no auto-scroll while a row is dragged (10 rows on a small phone). Add edge auto-scroll.
  - The wiggle (`ui/components/PlayerCard.kt:60-75`, infinite transition) should stop when "Remove animations" is on (animator duration scale 0); the dashed border alone is enough. Check how Compose behaves at scale 0 and gate it on `Settings.Global.ANIMATOR_DURATION_SCALE`.
- **List drop = swap** is a handoff decision, so reorder libraries (which do live moves) don't fit there.

### 13. Repository writes on the composition scope; no ViewModels (P2, S/M)
- **Custom:** `ui/round/RoundRoute.kt:103-107,226-227` (`RoundController.run` on `rememberCoroutineScope()`), `ui/scan/ScanRoute.kt:57,78-89`, `ui/newgame/NewGameScreens.kt:462,479-503`, `ui/players/PlayerEditor.kt:242-283`, `debug/DebugHomeState.kt:63-68`.
- **Problem:** the scope is cancelled when the route leaves composition (back, `stack.reset`, the GameOver `replaceTop`). A running `withTransaction` is then rolled back. The data stays consistent, but the action (a drop, a bust from the sheet, a view toggle) is silently lost.
- **Native:** `ViewModel` + `viewModelScope` (`lifecycle-viewmodel-compose`). With Navigation 3, ViewModels are scoped per back-stack entry and survive rotation. Today there is no `ViewModel` anywhere.
- **Quick fix now (S):** an app-wide `CoroutineScope(SupervisorJob() + Dispatchers.Default)` in `AppGraph` for fire-and-forget repository writes.
- **Real fix (M):** ViewModels per route, together with #5.

### 14. Strings and localisation (P2, L, replace later)
- **Custom:** `res/values/strings.xml` only has `app_name`. Every label, hint, content description and error is a Kotlin literal. Hand-built language rules:
  - plurals: `ui/sheet/PlayerSheet.kt:290` ("point/points"), `ui/scan/ScanCamera.kt:442` ("card seen/cards seen"), `ui/history/ScoreboardScreen.kt:93` ("round/rounds"), `ui/home/DebugTaps.kt:19`, `ui/sheet/SheetText.kt:13-17` ("hasn't/haven't");
  - English ordinals: `ui/gameover/Standings.kt:19-28`;
  - "A, B and C": `ui/gameover/Standings.kt:31-35`;
  - error text matched on an exception message: `ui/players/PlayerEditor.kt:266-268`.
- **Native:**
  - `stringResource` and `pluralStringResource` in Compose;
  - ICU `android.icu.text.MessageFormat("{0,ordinal}")` (or `RuleBasedNumberFormat`);
  - `android.icu.text.ListFormatter.getInstance().format(names)` (API 26+);
  - typed exceptions or error codes instead of message matching.
- **Strategy that keeps the JVM tests:** keep the pure helpers but have them return a resource id plus arguments, or pass in formatters. Do it package by package, and give each batch its own `res/values/strings_<area>.xml` so parallel agents don't conflict.

### 15. Font scaling (P1, M, audit later)
- **Custom:**
  - fixed `height(...)` with `sp` text: card and row size classes `ui/components/PlayerUi.kt:72-108`, 64 dp bottom buttons, 20–30 dp tags (`StatusTag.kt:52`, card tag heights), scoreboard cells;
  - very small text: 8–9 sp (`ui/history/ScoreboardScreen.kt:199,234,262,279`) and 10 sp (`Type.kt:83,94`).
- At 130–200 % (Android 14+ uses non-linear scaling) labels get cut or ellipsized, and fixed-height tags clip.
- **Native:** `heightIn(min = …)` instead of `height`; `@PreviewFontScale` / `@PreviewScreenSizes` multi-previews for every screen. Cap the scale (local `Density` with a clamped `fontScale`) only where the table geometry truly needs it, e.g. the 72 × 96 compact cards, and say so in a comment.
- Not a single component swap, but a real accessibility gap. Batch per package.

### 16. Icons and RTL (P2, S–M, replace later)
- **Custom:** three copies of the same idea:
  - `LineIcon` (`ui/components/SetupChrome.kt:318-359`),
  - `PathIcon` (`ui/components/Icons.kt:28-40`),
  - `StrokeIcon` (`ui/scan/ScanParts.kt:110-127`).
- There are two different `BackIcon` paths (`SetupChrome.kt:329` vs. `ScanParts.kt:124`). Canvas icons don't mirror, so in RTL (the manifest has `supportsRtl="true"`) back and chevron point the wrong way.
- **Native:** build `ImageVector`s once from the same path strings (`ImageVector.Builder` + `PathParser().parsePathString(d).toNodes()`), set `autoMirror = true` for back and chevron, draw them with M3 `Icon(imageVector, contentDescription = null, tint = …)`. There is no need for the Material icon set: keep our paths.

### 17. Edge-to-edge setup (P3, S, replace now)
- **Custom:**
  - `res/values/themes.xml:8-15` and `res/values-v31/themes.xml` set `statusBarColor`, `navigationBarColor` and `windowLight*`, which are deprecated and ignored when edge-to-edge is enforced on API 35+;
  - `ui/theme/Theme.kt:60-70` forces light bar icons in a `SideEffect`;
  - `MainActivity.kt:40` calls plain `enableEdgeToEdge()`, which picks the bar style from the system theme, so on a light system theme the first frames can show dark icons.
- **Native:** `enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(TRANSPARENT))`, then delete the `SideEffect` and the XML bar items. Keep `windowBackground`.

### 18. Insets helpers (P3, S, replace later)
- **Custom:** `ui/components/SetupChrome.kt:79-86` (`composed { windowInsetsPadding(statusBars ∪ 48dp) }`), `ui/home/HomeScreen.kt:170` (`composed` for the debug taps), and screens that combine `statusBars` / `navigationBars` by hand.
- **Native:** `windowInsetsPadding` is already a `Modifier.Node`, so it doesn't need `composed` (which is discouraged). Use `WindowInsets.safeDrawing` / `safeContent` so display cutouts are covered too.
- On Android 16+ large screens the system ignores `screenOrientation="portrait"` (manifest comment), so landscape with side cutouts can happen there.

### 19. Dialog (P2, S, keep + add pane title)
- `ui/components/SetupChrome.kt:237-249`: `Scan7Dialog` builds on `androidx.compose.ui.window.Dialog`, which gives its own window, focus trapping, Back and outside-tap dismissal. That is the right base.
- **Gaps:** no `paneTitle` (TalkBack doesn't say "dialog"; M3 `BasicAlertDialog` sets it) and no exit animation (the dialog just disappears).
- **Recommendation:** keep the wrapper. Either use M3 `BasicAlertDialog(onDismissRequest, properties)` inside it with our `DialogPanel` as content, or add `Modifier.semantics { paneTitle = title }` to `DialogPanel`.

### 20. `BottomSheetPanel` preview copy (P3, S, replace now)
- `ui/components/SetupChrome.kt:205-234` is a second sheet look with the fake grab handle and an empty `clickable` to "swallow taps". That adds a stray click action for TalkBack. It is now only used in previews and the gallery (`PlayerSheet.kt:447-476`, `PlayerEditor.kt:302-313`).
- Replace it with a preview-only `Column` that has the same background and border and reuses the private `SheetGrabHandle`, or delete it and preview `Scan7BottomSheet` content directly.

### 21. Settings storage (P3, S, replace later)
- **Custom:** Room `settings` key/value table (`data/Daos.kt:113-120`, `data/GameRepository.kt:53-66,213-219`) plus a separate SharedPreferences file `camera` for the zoom (`camera/CameraPreview.kt:58-59,109-110,173`). The zoom is read synchronously in composition.
- **Assessment:** the Room table is a reasonable choice here. `setViewMode` writes the game and the preference in **one transaction**, it is already a `Flow`, it is migrated and tested. DataStore would add a second store without fixing anything.
- **Recommendation:** keep Room and move the zoom into the same table, so there is one store and one backup path. Revisit Preferences DataStore only if settings grow into a real settings screen. Do this after batches B and C: it touches both `camera/` and `data/`.

### 22. Training-sample JSON (P3, S–M, replace later)
- **Custom:** `training/TrainingSample.kt:65-140`, a hand-built JSON writer with escaping. Plus `SimpleDateFormat` (`:66,118`).
- The reason given ("no org.json, so JVM tests work") also holds for kotlinx.serialization, which runs on the JVM.
- **Classpath:** only `kotlinx-serialization-core` arrives (transitively; the `constraints` block in `app/app/build.gradle.kts:96-99` is for androidTest). There is no `-json` artifact and no compiler plugin.
- **Native:** the `org.jetbrains.kotlin.plugin.serialization` plugin + `kotlinx-serialization-json`, with `@Serializable` and `@SerialName("timestamp_millis")` etc. and `Json { prettyPrint = true; explicitNulls = true }`; `java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME` (minSdk 29 has `java.time`).
- **Risk:** the output must stay compatible with `ml/scripts/import_app_scans.py`. Keep the field names and keep the `TrainingSampleTest` expectations (key order may change).
- The same plugin is needed for Navigation 3 keys (#5).

### 23. Text fields (P2, S, replace later)
- `ui/components/SetupChrome.kt:277-314` (`Scan7TextField`) and `ui/sheet/AdjustTotalDialog.kt:123-151` (`DeltaField`) use `BasicTextField` with IME actions and keyboard type (good).
- **Gaps:**
  - focus isn't shown (the comment admits it): use `interactionSource.collectIsFocusedAsState()` → accent border;
  - the label is set as `contentDescription`; check with TalkBack whether the typed text is still read, and prefer a visible label `Text` merged with the field;
  - the input filters (`filterDeltaInput`, `take(MAX_NAME_LENGTH)`) live in `onValueChange` and can jump the cursor.
- **Native:** `BasicTextField(state = rememberTextFieldState(), inputTransformation = …, lineLimits = SingleLine)`, plus `KeyboardOptions(autoCorrectEnabled = false)` for names.
- Keep the custom ± toggle: number keyboards often have no minus key, so it is justified.

### 24. Target stepper (P2, S, replace later)
- `ui/newgame/NewGameScreens.kt:402-435`: [−] value [+] takes three TalkBack stops.
- **Native:** merge them into one node with `semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, MIN..MAX, steps); setProgress { … } }`. TalkBack then treats it like a slider (swipe up/down or volume keys). Keep the visible buttons.
- Same idea for the adjust-total steppers if wanted.

### 25. Scoreboard grid semantics (P2, S, replace later)
- `ui/history/ScoreboardScreen.kt:108-117,214-222`: the cells have good descriptions, but TalkBack doesn't know it is a table.
- **Native:** `semantics { collectionInfo = CollectionInfo(rows, columns) }` on the grid and `collectionItemInfo = CollectionItemInfo(row, 1, col, 1)` on each cell. TalkBack then says "row 3, column 2".

### 26. Splash screen (P3, S, replace later)
- **Custom:** only `windowSplashScreenBackground` in `res/values-v31/themes.xml`. Android 10–11 (inside minSdk) get no branded splash. The first DB load shows blank `Loading()` boxes (`AppNavHost.kt:177-180`, `RoundRoute.kt:232-235`, `ScanRoute.kt:67-69`).
- **Native:** `androidx.core:core-splashscreen`: `Theme.SplashScreen` parent + `installSplashScreen()` in `MainActivity`, with `setKeepOnScreenCondition { first snapshot not loaded }`. One splash on API 29+ and no blank frame.

### 27. View toggle and zoom pills: keep
- `ui/components/Buttons.kt:261-304`, `ui/scan/ScanCamera.kt:372-405`. M3 `SegmentedButton` is an outlined, check-mark segmented row with item shapes; making it look like the handoff's pill-in-pill (14/10 radii, no border, filled active segment) would mean fighting the component.
- Semantics are already right: `selectable` + `selectableGroup`. Minor: `ViewToggle` uses `Role.Tab`, the zoom pills `Role.RadioButton`; pick one (RadioButton is the closer match).

### 28. Buttons, chips, grid toggles, status tags: keep
- `ui/components/Buttons.kt`, `ui/scan/ManualEntry.kt:269-348` (`toggleable(role = Checkbox)` + count state), `ui/scan/ScanParts.kt` / `ui/sheet/PlayerSheet.kt:396-428` (`CardChip`).
- These carry the card token look (handoff §3) and use foundation semantics correctly (`clickable` / `toggleable` / `selectable` with roles, `stateDescription`, custom actions). M3 `Button` / `FilterChip` would only add defaults we'd override.
- **Duplication:** two `CardChip` composables (`ui/sheet/PlayerSheet.kt:396` and `ui/scan/ScanParts.kt:166`) and two `SectionLabel`s (`SetupChrome.kt:130`, `ManualEntry.kt:222`). Merge them later (P3).

### 29. Already platform: keep
- **Camera permission:** `camera/CameraPermission.kt` uses `rememberLauncherForActivityResult(RequestPermission)` + `LifecycleResumeEffect`. Optional: use `shouldShowRequestPermissionRationale` to show only "Open settings" after "Don't ask again".
- **Photo picker:** `debug/PhotoDetectScreen.kt:62-65` uses `PickVisualMedia`; `ImageDecoder` applies EXIF orientation.
- **MediaStore:** `training/TrainingSaver.kt:100-153` uses `IS_PENDING`, `RELATIVE_PATH` and needs no storage permission.
- **Detector thread:** `detection/CardDetector.kt:164-183` uses a single-thread executor + `asCoroutineDispatcher()`. This is justified: the GPU delegate is bound to its thread and CameraX needs an `Executor`. `limitedParallelism(1)` would not pin the thread.
- **Training saver scope:** `TrainingSaver.kt:41` (`SupervisorJob + IO`, outlives the screen) is fine; WorkManager would be overkill.
- **Lifecycle:** `collectAsStateWithLifecycle` is used everywhere.
- **Bottom sheets:** `Scan7BottomSheet` (`SetupChrome.kt:162-191`) is the restyled `ModalBottomSheet`, as intended.

### 30. Smaller items (P3, S each, later / optional)
- **Debug switch:** `debug/DebugHomeScreen.kt:226-248` is a hand-drawn switch. M3 `Switch` with `SwitchDefaults.colors(...)` from the tokens does the same, debug only.
- **Editor state not saved:** `ui/players/PlayersScreen.kt:204` and `ui/newgame/NewGameScreens.kt:459` keep `editor` in plain `remember`, so it is lost on rotation or process death. Use `rememberSaveable` with a small saver.
- **Backup rules:** `res/xml/backup_rules.xml` and `data_extraction_rules.xml` are still the IDE templates (TODO comments). Today the full default backup applies, DB included, which is probably what we want. Make it explicit: include `database/scan7.db` and the prefs, and state that the training images live in shared storage.
- **Baseline Profile:** none. `profileinstaller` comes in through Compose. A `:baselineprofile` module (Macrobenchmark) would speed up cold start and the first Round frame. M, optional.
- **App shortcuts:** a "Resume game" / "New game" launcher shortcut via `ShortcutManagerCompat` needs intent → initial stack, which works the same way as the debug hooks. Product decision.
- **Share results:** nothing to share from Game over or the Scoreboard. `ShareCompat.IntentBuilder` / `Intent.createChooser(ACTION_SEND)` with a text summary. Product decision.
- **Lists:** `Column` + `verticalScroll` for players, scoreboard and the round list view is fine at these sizes; switch to `LazyColumn` only where #11 needs `animateItem`.

---

## Proposed order of work

Split by package so parallel agents don't touch the same files. **Batch A owns every Gradle and manifest change**, including the dependencies B and C need, so `libs.versions.toml` / `build.gradle.kts` aren't edited in parallel. Each batch puts its extracted strings in its own `res/values/strings_<a|b|c>.xml`.

### Batch A: foundation (can start now)
Packages: `ui/components`, `ui/theme`, `MainActivity.kt`, `AndroidManifest.xml`, `res/`, `app/app/build.gradle.kts`, `app/gradle/libs.versions.toml`.
1. #3 theme defaults (shapes, `LocalRippleConfiguration` null, `LocalIndication` = PressDim).
2. #4 `enableOnBackInvokedCallback="true"`.
3. #17 `enableEdgeToEdge(SystemBarStyle.dark)`; clean up themes.xml.
4. #19 pane title in `Scan7Dialog` / `DialogPanel`.
5. #20 retire `BottomSheetPanel`'s fake handle and click swallow.
6. #16 icons as `ImageVector` with `autoMirror` (components only; leave `ScanParts` icons to B).
7. #18 insets helpers without `composed`.
8. #23 focus border for `Scan7TextField`.
9. #26 splash.
10. #30 backup rules.
11. Dependencies for later: `kotlinx-serialization-json` + plugin, `core-splashscreen`, `lifecycle-viewmodel-compose`, Calvin-LL `reorderable` (only if #11 is approved), and Navigation 3 only if #5 is approved.
12. #14 / #15 for `ui/components`.

### Batch B: game flow (after the current ui/round and ui/scan worktrees are merged)
Packages: `ui/round`, `ui/scan`, `camera`, `detection` (overlay only).
1. #1 snackbar behind the existing `BustToastState` API (so `ui/nav` doesn't change).
2. #6 `DropdownMenu` for the ▾ menu (or pass this to the ui/scan agent now).
3. #7 torch state.
4. #9 `Modifier.keepScreenOn()`.
5. #8 haptics (round + scan).
6. #12 list-view auto-scroll + reduced-motion wiggle.
7. #10 tap-to-focus (then exposure / pinch if wanted).
8. #16 for `ScanParts` icons.
9. #14 / #15 for these packages.

### Batch C: setup screens, data, training, debug (can run in parallel with A, and with B)
Packages: `ui/home`, `ui/players`, `ui/newgame`, `ui/history`, `ui/gameover`, `ui/sheet`, `data`, `training`, `debug`. (Not `ui/nav`.)
1. #2 toggleable checkbox rows.
2. #11 seating `LazyColumn` + reorder with auto-scroll and haptics.
3. #24 adjustable target stepper.
4. #25 scoreboard collection semantics.
5. #14 strings with ICU ordinals and `ListFormatter`.
6. #15 font scaling.
7. #22 kotlinx.serialization for training JSON (needs A's dependency).
8. #13 quick fix: app-wide scope in `AppGraph` for repository writes.
9. #30 debug switch and saveable editor state.

### Follow-up D: sequential, after A–C are merged, needs your decision
Packages: `ui/nav` plus the route call sites.
1. #5 Navigation 3 migration.
2. #13 ViewModels per entry.
3. #21 move the zoom preference into the settings table.

D runs alone because it touches the call sites of every route.

## Needs a decision from you
1. **Navigation 3 instead of the self-made back stack** (#5). It changes the logged decision of 2026-10-18.
2. **A third-party reorder library** (Calvin-LL Reorderable) for the seating list (#11), or keep our drag code and add auto-scroll ourselves.
3. **Keep the screen on during the whole game** (#9), not only on the camera.
4. **Translation:** is moving the strings to resources worth the L effort now (#14), or only once a second language is planned?
5. Optional features: **share results** and **launcher shortcuts** (#30).
