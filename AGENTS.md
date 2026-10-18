# AGENTS.md

Read this first. It applies to every agent (Claude Code and others) working in this repo.
Keep it short, factual and current: when a command, rule or decision changes, update it here.

## What Scan7 is
An Android app that scans one player's row of Flip 7 cards with the phone camera, detects each
card on-device (YOLO → LiteRT), and computes the score. Players can correct cards before the
score is saved. Personal / open-source project.

- Plan and milestones: [docs/BRIEF.md](docs/BRIEF.md) (living document)
- **Design (look, layout, tokens, components): [design/DESIGN_HANDOFF.md](design/DESIGN_HANDOFF.md)** + prototype `design/reference/RoundScreen.dc.html` (px in the handoff = dp in Compose).
- App flow / game screens spec: [docs/APP_FLOW.md](docs/APP_FLOW.md); screen inventory for design: [docs/SCREENS.md](docs/SCREENS.md)
- Setup, data layout and stage status: [README.md](README.md)
- Brand kit: [brand/BRAND.md](brand/BRAND.md)
- Labeling workflow: [ml/labeling/README.md](ml/labeling/README.md)

## Repo layout
```
AGENTS.md, CLAUDE.md   agent instructions (CLAUDE.md only imports this file)
README.md              setup + stage table (commands per stage)
docs/                  BRIEF.md (project brief), APP_FLOW.md (screen spec), third_party/ (font licenses)
brand/                 logo SVG/PNG, colors.xml, BRAND.md
ml/                    Python (uv project): data, synthetic generator, training, export
  config/              cards.yaml (class list, geometry), synth.yaml (generator)
  scripts/             make_cutouts, gen_synth, preview_synth, train, predict, export
  labeling/            Label Studio setup, YOLO export, pre-label import
  data/                photos (NOT in git; index in data/manifest.csv)
  models/ runs/ out/   pretrained weights, training runs, generated outputs (gitignored)
app/                   Android Gradle root; module app/, package net.moesli.scan7
  app/src/main/java/net/moesli/scan7/
    scoring/           plain-Kotlin scoring rules (JVM unit tests)
    game/              plain-Kotlin game state + rules (GameState: rounds, bust, close, seating; JVM unit tests)
    data/              Room DB (entities, DAOs), GameRepository (Flows + actions), AppGraph container
    camera/ detection/ CameraX preview, detector, overlay
    ui/nav/            app navigation: Route sealed class + self-made back stack (AppNavHost; Home is the start screen)
    ui/round/          Round screen + RoundRoute/RoundController (snapshot → RoundUiState, actions → repository)
    training/          "Save scans for training" (JPEG + JSON sidecar via MediaStore; setting, default on)
    debug/             debug menu (Detector: live camera, detect from photo, model/backend info · Training data: save-scans toggle), hidden: tap the Home wordmark 5× (all builds; TalkBack action "Open debug menu"); the scan-flow demo has no menu entry (only the debug_scan_demo / debug_scan_photo hooks)
    ui/theme/          Scan7 theme, colors, fonts, wordmark
  app/schemas/         exported Room schemas (committed; bump the DB version + add a migration on change)
```

## Commands

### ML (`cd ml` first; `uv sync` once)
```sh
uv run python scripts/make_cutouts.py                 # stage 1: cards_flat -> out/cutouts (+ --measure, --debug)
uv run python scripts/gen_synth.py --count 5000 --seed 1 --workers 10   # stage 2 -> out/synth
uv run python scripts/preview_synth.py --split val --grids 2 --full 5   # -> out/synth_preview
uv run python scripts/train.py --data out/synth/dataset.yaml --name synth_v1   # YOLO26n on MPS
uv run python scripts/train.py --data out/mix/dataset.yaml --model runs/synth_v1/weights/best.pt --name ft_v1
uv run python scripts/predict.py runs/synth_v1/weights/best.pt data/real_photos/train --out out/pred/v1/train
uv run python scripts/import_app_scans.py           # pull app training scans from the phone -> real_photos (80/20), LS sync; train: auto-accept if the detections match the cards the user confirmed, else pre-label + hint; test: hint only
uv run python -m unittest discover -s tests         # Python unit tests (ml/tests)
uv run scripts/export.py runs/synth_v1/weights/best.pt  # own PEP 723 env: NOT `uv run python`; installs into app assets
```
The app's `model.tflite` is **not in git** (gitignored, ~10 MB per version): after a fresh clone, run the export once before building the app.
Debug-only app hooks (debug builds): `adb shell am start -n net.moesli.scan7/.MainActivity --es debug_photo /data/data/net.moesli.scan7/files/x.jpg [--ez debug_cpu true]`
(copy the photo in with `adb push x.jpg /data/local/tmp/ && adb shell run-as net.moesli.scan7 cp /data/local/tmp/x.jpg files/`).
`--es debug_scan_demo camera|next|edit|bust|flip7|empty` / `--es debug_scan_photo <path>`: the scan flow with a fake player (demo screen, no menu entry).
`--es debug_game_photo <path>`: every camera scan in the real game flow opens the review on that photo (emulator end-to-end tests).
Debug menu toggle "Save scans for training" (default **on**): every shutter photo → `/sdcard/Pictures/Scan7 Training/scan7_<ts>.jpg` + `/sdcard/Documents/Scan7 Training/scan7_<ts>.json`; outcome `null` until confirmed (`adb pull "/sdcard/Pictures/Scan7 Training"`); cards the user left out on the review are `"excluded": true` per detection and in `outcome.excluded`.
Release: `./gradlew assembleRelease` (minified, unsigned; `-Pscan7.idSuffix=.p` works for release too). The build fails early (`checkModelAsset`, before preBuild) if `assets/model.tflite` is missing.
```sh
```

### Labeling (from the repo root; Label Studio is a separate `uv tool`)
```sh
ml/labeling/start.sh                                          # http://localhost:8080
uv run --project ml python ml/labeling/setup_projects.py      # create projects / sync photos
uv run --project ml python ml/labeling/make_config.py         # regenerate label config from cards.yaml
uv run --project ml python ml/labeling/export_yolo.py         # -> ml/out/real/{train,test}
uv run --project ml python ml/labeling/ai_review.py prepare|upload --split train|test   # AI-assisted labeling of unlabeled tasks (review sheets -> decisions.json -> annotations marked 'Labeled by Claude')
uv run --project ml python ml/labeling/import_predictions.py ml/out/pred/v1/train/labels --model-version v1   # train project only
```

### Android (`cd app`)
```sh
export JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"   # adb
./gradlew assembleDebug            # build
./gradlew testDebugUnitTest        # JVM unit tests (scoring etc.)
./gradlew lintDebug                # lint
./gradlew installDebug             # install on the connected phone
ANDROID_SERIAL=emulator-5554 ./gradlew -Pscan7.idSuffix=.b installDebug   # parallel agents: side-by-side copy on the shared emulator. ALWAYS set ANDROID_SERIAL: installDebug otherwise installs on every device incl. the user's phone
./gradlew --stop                   # stops ALL Gradle daemons of this version on the Mac, incl. other agents' running builds: only when no other agent is building
```
The test phone (Pixel 10 Pro) is connected over **wireless debugging** (adb-tls-connect via
mDNS); `adb devices` should list `adb-…._adb-tls-connect._tcp`. If it is missing, re-enable
Wireless debugging on the phone (pair once with `adb pair <ip>:<port>`). Only one agent should
drive the phone at a time.

## Hard rules
- **Class order is fixed** by `ml/config/cards.yaml` (ids 0–22 = YOLO class ids = app labels). Never reorder.
- **Photos are not in git.** `PRIVATE_*` files show personal info: never upload, share, paste or send them anywhere.
- **The real test set** (`ml/data/real_photos/test`, Label Studio project "scan7 test") is never used for training and never pre-labeled.
- **Never stop or kill running training jobs** (or their python processes); don't touch `ml/runs/` of a running job.
- The Mac has **16 GB RAM**: keep memory use modest (one Gradle build at a time, moderate `--workers`/`--batch`, stop daemons when done).
- Android Studio may hold `.git/index.lock` briefly: **retry** the git command, never delete the lock.
- Don't commit secrets. `ml/labeling/ls_data/` (DB, credentials) is private and gitignored.
- Commits: the coordinating session commits each reviewed, finished step locally (approved by the user; no pushing without asking). Subagents working in parallel don't commit; they report their changed files.

## Decisions log
| Date | Decision |
|---|---|
| 2026-10-18 | Detector: **Ultralytics YOLO26n** (AGPL; fine because the app is personal/open source; a commercial release needs an Ultralytics license or an Apache-licensed model). |
| 2026-10-18 | The app scans **one player's row per scan**; neighbouring rows may be partly visible. |
| 2026-10-18 | Android **minSdk 29**; Kotlin + Jetpack Compose + Material 3; CameraX; LiteRT. |
| 2026-10-18 | **Dark brand theme always** (no light variant, no dynamic color); brand colors override Material defaults. |
| 2026-10-18 | **Design handoff adopted** (design/DESIGN_HANDOFF.md): custom components, no Material default look; DM Mono for all text except Unbounded where specified; players = palette color + initial (max 10); table view with free card positions + list view; one-tap bust with undo; "Scan next" chains cameras; end of round reuses the table/list layout. Ask the user before deviating from the handoff. |
| 2026-10-18 | **Navigation:** small self-made back stack (`ui/nav`, routes saved as strings via rememberSaveable), no navigation-compose. List-view arrange drop **swaps** the two players (`GameState.swapSeats`). Default table positions: one ellipse 0.485 × 0.5 in `game/Seating.kt`. |
| 2026-10-18 | **Languages: English (default) + German**, Android string resources (`values/`, `values-de/`), follows the system language + per-app language setting (`locales_config`). German: "BUST" → **"RAUS"**; card names stay as printed (Freeze, Flip Three, Second Chance, Flip 7). |
| 2026-10-18 | **Multi-phone play (later):** Google Nearby Connections (P2P_STAR, host-authoritative, QR join), fully local, no online service. Research + plan in [docs/MULTI_DEVICE.md](docs/MULTI_DEVICE.md); not started yet. |
| 2026-10-18 | **App flow agreed** — spec in [docs/APP_FLOW.md](docs/APP_FLOW.md): players stored locally (no groups), select who plays; scan with the **shutter button only** (no auto-capture); bust shown in magenta **plus** the word; the game ends only after a full round is scored, highest total wins. |
| 2026-10-18 | **Total adjustments** ("Adjust total" in the player sheet): own table `score_adjustments` (DB v3, AutoMigration 2→3, MigrationTest in androidTest), counted in `GameState.totals`; no scoreboard row, only a "±" mark on the total. |

## Branding (summary; details in brand/BRAND.md)
- App name **Scan7**; wordmark "scan" in text color + "7" in accent (`Scan7Wordmark`).
- Colors "Deep Cyan": ground `#0A1220`, surface `#15223A`, text `#EEF4FA`, muted `#93A4BC`, accent `#38D6F5`, bust `#FF4F9A`.
  In Compose use `MaterialTheme.colorScheme` (mapped to these) or `Scan7Theme.colors` for brand-only tokens.
- **Bust = magenta, always together with the word "Bust" and/or an icon** — never color alone, never orange or red (card colors).
- **Scan boxes: cyan with a white outline** (`drawScanBox` / `Scan7Theme.colors.scanBox` + `scanBoxOutline`), visible on the light-blue Freeze card.
- Fonts: **Unbounded** (Bold/SemiBold) for wordmark, big numbers, display/headline; **DM Mono** for titles/labels/buttons; system font for body text. Licenses in `docs/third_party/`.
- **Never use Flip 7's artwork, fonts, logo or name styling.**

## Code conventions
- **Platform first:** before building a UI behaviour or system feature yourself, use the Android / Jetpack / Material 3 component that already does it (bottom sheets, dialogs, snackbars, drag & drop, pull/scroll, text fields, permissions, haptics, back handling, accessibility actions, photo picker, settings storage …) and **restyle it** with the Scan7 tokens. Custom code only when the platform can't do it, with a one-line comment why.
- Kotlin/Compose: follow the existing style (4-space indent, trailing commas, `modifier: Modifier = Modifier` as the first optional parameter, small private composables, `@Preview` wrapped in `Scan7Theme`). Use theme colors/typography, no hardcoded colors.
- Game logic (scoring, smoothing, …) is plain Kotlin without Android dependencies, covered by JVM unit tests in `app/app/src/test`.
- Python: scripts in `ml/scripts/` runnable via `uv run python scripts/<name>.py` from `ml/` with argparse and sensible defaults; module docstring with usage example; paths relative to the `ml/` root.
- Configuration lives in `ml/config/*.yaml`, not in code.
- Keep README.md's stage table and commands current when you change a stage.
