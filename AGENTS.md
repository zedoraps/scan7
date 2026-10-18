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
- Release (Play / GitHub / F-Droid, signing, versioning, model download, store listing): [docs/RELEASE.md](docs/RELEASE.md), [docs/store/](docs/store/), privacy policy [docs/privacy.md](docs/privacy.md)

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
    training/          "Save scans for training" (JPEG + JSON sidecar via MediaStore; setting, default on in debug builds, always off in release)
    debug/             debug menu (Detector: live camera, detect from photo, model/backend info · Training data: save-scans toggle), hidden: tap the Home wordmark 5× (debug builds only, BuildConfig.DEBUG; TalkBack action "Open debug menu"); the scan-flow demo has no menu entry (only the debug_scan_demo / debug_scan_photo hooks)
    ui/about/          About & licenses (Home: tap the version footer): version, AGPL + source link, privacy link, third-party notices (static list; texts from LICENSE + docs/third_party/ copied into assets at build time)
    ui/theme/          Scan7 theme, colors, fonts, wordmark
    multiplayer/       (multiplayer branch only) plain-Kotlin multi-phone protocol: GameAction, Host/ClientSession, GuestRules, FakeNetwork; nearby/ = Nearby adapter on an owned ConnectionsApi (+ permissions, QR link); not wired into the UI
  app/src/play/        (multiplayer branch, only with -Pscan7.flavors=true) GmsConnectionsApi + Nearby permissions manifest
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
The app's `model.tflite` is **not in git** (gitignored, ~10 MB per version): after a fresh clone, `cd app && ./gradlew downloadModel` (released model from `app/model.properties`, SHA-256 checked) or run the export once before building the app.
Debug-only app hooks (debug builds): `adb shell am start -n net.moesli.scan7/.MainActivity --es debug_photo /data/data/net.moesli.scan7/files/x.jpg [--ez debug_cpu true]`
(copy the photo in with `adb push x.jpg /data/local/tmp/ && adb shell run-as net.moesli.scan7 cp /data/local/tmp/x.jpg files/`).
`--es debug_scan_demo camera|next|edit|bust|flip7|empty` / `--es debug_scan_photo <path>`: the scan flow with a fake player (demo screen, no menu entry).
`--es debug_game_photo <path>`: every camera scan in the real game flow opens the review on that photo (emulator end-to-end tests).
`--es debug_seed p3|p6|p8|p10|gameover|history [--es debug_open home|players|newgame|seating|round_table|round_list|sheet_open|sheet_done|adjust_dialog|manual|review|camera|scoreboard|gameover]`: **wipes the app's DB**, stores 10 players + one deterministic game (round 6 running with scored/bust/open, leader, long name; or game over; `history` = p6 + five past games for the Home history) and opens that screen (`debug/DebugSeed.kt`; sheet_*/adjust_dialog open the table, the matrix script taps; review scans `files/screen_review.jpg`).
**Screenshot matrix** (use a `.shots` install on emu-1 + emu-3 only): `scripts/screen-matrix.sh --install` → `out/screens/<WxH@dpi x font>/<scenario>_<screen>.png` + contact sheets `out/screens/sheet_<scenario>_<screen>.jpg` (14 profiles: phones 16:9…21:9, foldable inner, tablet, font 1.3/2.0; filter with `PROFILES=… SCREENS=… DEVICES=…`; resets wm size/density/font_scale at the end).
Debug menu toggle "Save scans for training" (debug builds only, default **on**; release builds never save): every shutter photo → `/sdcard/Pictures/Scan7 Training/scan7_<ts>.jpg` + `/sdcard/Documents/Scan7 Training/scan7_<ts>.json`; outcome `null` until confirmed (`adb pull "/sdcard/Pictures/Scan7 Training"`); cards the user left out on the review are `"excluded": true` per detection and in `outcome.excluded`.
Release: `./gradlew assembleRelease` / `bundleRelease` (minified; signed only when the upload key is set in `~/.gradle/gradle.properties` `scan7.upload.*` or `SCAN7_UPLOAD_*` env, else unsigned; `-Pscan7.idSuffix=.p` works for release too). versionCode = git commit count, versionName = `scan7.versionName` in app/gradle.properties (docs/RELEASE.md). The build fails early (`checkModelAsset`, before preBuild) if `assets/model.tflite` is missing.
```sh
```

### Training on mini (`ssh pmo@mini`, NixOS, CPU only; details in `~/scan7-ml/README-mini.md` there)
mini has no Intel GPU compute runtime (no XPU), so it trains on its 16 CPU cores. Its env is separate
(`~/scan7-ml/.venv`, Python 3.13, `requirements-mini.txt`); don't `uv sync` there. Dataset yamls on mini use `/home/pmo/scan7-ml` paths.
```sh
rsync -a --exclude '*.cache' ml/out/<dataset>/ pmo@mini:scan7-ml/out/<dataset>/   # data up (then fix `path:` in its yaml to /home/pmo/scan7-ml/...)
rsync -a ml/scripts/ pmo@mini:scan7-ml/scripts/                                     # keep scripts identical
ssh pmo@mini 'cd ~/scan7-ml && . ./env-mini.sh && nohup nice -n 10 python scripts/train.py --device cpu ... --name <name> > train_<name>.log 2>&1 &'
ssh pmo@mini 'tail -n 3 ~/scan7-ml/runs/<name>/results.csv; tail -c 400 ~/scan7-ml/train_<name>.log'   # check
mkdir -p ml/runs/<name>/weights && rsync -a pmo@mini:scan7-ml/runs/<name>/ ml/runs/<name>/          # weights + results back
```
Resume an interrupted run: `scripts/train.py --resume runs/<old>/weights/last.pt --data <abs yaml> --device cpu --name <new>`.

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
./gradlew -Pscan7.flavors=true assembleDebug testPlayDebugUnitTest lintPlayDebug   # multiplayer branch: foss/play flavors (play = Play services Nearby); without the flag the build is flavorless + Play-free
./gradlew --stop                   # stops ALL Gradle daemons of this version on the Mac, incl. other agents' running builds: only when no other agent is building
```
The test phone (Pixel 10 Pro) is connected over **wireless debugging** (adb-tls-connect via
mDNS); `adb devices` should list `adb-…._adb-tls-connect._tcp`. If it is missing, re-enable
Wireless debugging on the phone (pair once with `adb pair <ip>:<port>`). Only one agent should
drive the phone at a time.

### Emulators on mini (headless, API 37 x86_64 Google APIs, 1080×2400, 2 GB RAM)
Three emulators run on mini and are tunneled to the Mac over SSH: serials **`localhost:5561` =
scan7-emu-1, `localhost:5563` = emu-2, `localhost:5565` = emu-3**. Use **one emulator per agent**
(pick a free one, don't share), and **always** set `ANDROID_SERIAL=localhost:556x` for every
`adb`/`./gradlew installDebug`/test command. **Never use the user's phone** (`adb-…` /
`192.168.178.26`) unless told to.
```sh
scripts/mini-emulators.sh start      # boot on mini (if needed), open tunnels, adb connect (idempotent)
scripts/mini-emulators.sh status     # remote state + tunnel + adb
scripts/mini-emulators.sh tunnel     # only reopen tunnels / reconnect adb (e.g. after `adb kill-server`)
scripts/mini-emulators.sh stop       # adb disconnect, close tunnels, stop the emulators
ANDROID_SERIAL=localhost:5561 ./gradlew installDebug
```
On mini: `~/android/emulators.sh start|stop|status` (SDK via Nix in `~/android`, logs in `~/android/logs`).
The emulators run at nice 5 next to training; idle they cost ~1.5 cores in total. The app data persists across restarts.

## Hard rules
- **Class order is fixed** by `ml/config/cards.yaml` (ids 0–22 = YOLO class ids = app labels). Never reorder.
- **Photos are not in git.** `PRIVATE_*` files show personal info: never upload, share, paste or send them anywhere.
- **The real test set** (`ml/data/real_photos/test`, Label Studio project "scan7 test") is never used for training and never pre-labeled.
- **Never stop or kill running training jobs** (or their python processes); don't touch `ml/runs/` of a running job.
- The Mac has **16 GB RAM**: keep memory use modest (one Gradle build at a time, moderate `--workers`/`--batch`, stop daemons when done).
- Android Studio may hold `.git/index.lock` briefly: **retry** the git command, never delete the lock.
- Don't commit secrets. `ml/labeling/ls_data/` (DB, credentials) is private and gitignored. The release upload keystore and its passwords stay outside the repo (`~/.gradle/gradle.properties` or env).
- Commits: the coordinating session commits each reviewed, finished step locally (approved by the user; no pushing without asking). Subagents working in parallel don't commit; they report their changed files.

## Decisions log
| Date | Decision |
|---|---|
| 2026-10-18 | Detector: **Ultralytics YOLO26n** (AGPL; fine because the app is personal/open source; a commercial release needs an Ultralytics license or an Apache-licensed model). |
| 2026-10-18 | The app scans **one player's row per scan**; neighbouring rows may be partly visible. |
| 2026-10-18 | Android **minSdk 29**; Kotlin + Jetpack Compose + Material 3; CameraX; LiteRT. |
| 2026-10-18 | **Dark brand theme always** (no light variant, no dynamic color); brand colors override Material defaults. |
| 2026-10-18 | **Design handoff adopted** (design/DESIGN_HANDOFF.md): custom components, no Material default look; DM Mono for all text except Unbounded where specified; players = palette color + initial (max 10; now avatars, see below); table view with free card positions + list view; one-tap bust with undo; "Scan next" chains cameras; end of round reuses the table/list layout. Ask the user before deviating from the handoff. |
| 2026-10-18 | **Navigation:** small self-made back stack (`ui/nav`, routes saved as strings via rememberSaveable), no navigation-compose. List-view arrange drop **swaps** the two players (`GameState.swapSeats`). Default table positions: one ellipse 0.485 × 0.5 in `game/Seating.kt`. |
| 2026-10-18 | **Languages: English (default) + German**, Android string resources (`values/`, `values-de/`), follows the system language + per-app language setting (`locales_config`). German: "BUST" → **"RAUS"**; card names stay as printed (Freeze, Flip Three, Second Chance, Flip 7). |
| 2026-10-18 | **Platform audit adopted** ([docs/PLATFORM_AUDIT.md](docs/PLATFORM_AUDIT.md)): batches A (components/theme/manifest/deps) → B (round/scan/camera) + C (setup/sheet/data/debug) in parallel → D alone later: **switch to Navigation 3**. Screen stays on in the camera **and the round screen**. Sheets close with their slide-down animation before running the action (Save, Delete, menu items). |
| 2026-10-18 | **Confirmations:** one `Scan7ConfirmDialog` (centred, never inline in a sheet; Cancel + action; 400 ms arm delay instead of press-and-hold); destructive = **warning style** (text-colour outline + ⚠), never cyan or bust magenta. Catalogue in `ui/components/Confirmations.kt` + docs/APP_FLOW.md. Round menu = M3 list + segmented scan direction. |
| 2026-10-18 | **int8 (milestone 5):** `export.py --int8 static_e2e` keeps [1,300,6], same accuracy, 3 MB; on the Pixel GPU no faster than fp32 (~70 ms), so the app ships **fp32** for now; int8 kept for CPU-only devices / smaller APK. Report: ml/out/export/INT8_REPORT.md (not in git). |
| 2026-10-18 | **Multi-phone play (later):** Google Nearby Connections (P2P_STAR, host-authoritative, QR join), fully local, no online service. Spec in [docs/MULTI_DEVICE.md](docs/MULTI_DEVICE.md): host shares by QR, guests pick who they are + whom they scan, guests only edit their own entries of the running round, no totals / round ending; Play services OK (`play` flavor), guests keep a "joined" history copy. Protocol spike on a separate branch, not in main. |
| 2026-10-18 | **App flow agreed** — spec in [docs/APP_FLOW.md](docs/APP_FLOW.md): players stored locally (no groups), select who plays; scan with the **shutter button only** (no auto-capture); bust shown in magenta **plus** the word; the game ends only after a full round is scored, highest total wins. |
| 2026-10-18 | **Total adjustments** ("Adjust total" in the player sheet): own table `score_adjustments` (DB v3, AutoMigration 2→3, MigrationTest in androidTest), counted in `GameState.totals`; no scoreboard row, only a "±" mark on the total. |
| 2026-10-18 | **Player avatars** (design/avatars/AVATARS.md): `Avatar(face, pattern, color)` ids on the player (DB v4, manual `MIGRATION_3_4` in data/Migrations.kt: old palette index → nearest catalog color, face + pattern seeded by player id; MigrationTest 3→4 and 1→4); `PlayerAvatar` replaces the initial token everywhere; colors may repeat within a game (editor dot hint, no New game blocking). Debug menu → Avatars shows the kit examples. |
| 2026-10-18 | **Open source under AGPL-3.0** (LICENSE; because of Ultralytics YOLO). Release config: upload-key signing from `~/.gradle/gradle.properties`/env (never in git), versionCode = commit count, debug menu + training-scan saving debug-only, static About & licenses screen, model as a GitHub release asset (`downloadModel`, SHA-256 pinned in app/model.properties). Checklist: docs/RELEASE.md. |
| 2026-10-18 | **Card-look variants without a photo** go to `ml/data/cards_extra/<class>_<desc>.png` (rectified RGBA 600×832; English `00_en_rainbow`, `back_en` cut from the render `paste_773e114e` by `scripts/extract_en_variants.py`); `make_cutouts.py` copies them to `out/cutouts/<class>_<n>` (map in `out/cutouts/sources.yaml`), pick rates + banner box in synth.yaml `variants`. Web test stems listed in **`ml/data/web_test/exclude.txt`** (the render and its small copy `paste_80304d69`) are training sources and must be skipped by every evaluation. |

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
