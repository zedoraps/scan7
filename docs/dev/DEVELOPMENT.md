# Development

This page covers setup, the data layout and the ML stages. The plan is in [BRIEF.md](BRIEF.md). Coding agents read [AGENTS.md](../../AGENTS.md) first; it has the commands, hard rules, decisions and conventions.

## Branding
The app is called **Scan7** and uses a dark "Deep Cyan" theme with the Unbounded and DM Mono fonts. The brand kit and its rules are in [brand/BRAND.md](../../brand/BRAND.md); in the app, see `ui/theme/` (`Scan7Theme`, `Scan7Theme.colors`, `Scan7Wordmark`). The font licenses are in [docs/third_party/](../third_party/).

## Layout
- `ml/` has the Python code for card cutouts, synthetic data, training and export.
- `app/` has the Android app (Kotlin and Jetpack Compose).
- `docs/` has the privacy policy, store listing and licences; `docs/dev/` has the plan, specs and release checklist.

## Setup

### ML environment
Requires [uv](https://docs.astral.sh/uv/). It fetches Python 3.12 automatically.

```sh
cd ml
uv sync                 # creates ml/.venv with Python 3.12 and installs deps
uv run python --version # prints Python 3.12.x
```

Run any script with `uv run python scripts/<name>.py` from `ml/`.

### Android
- The Android SDK is in `~/Library/Android/sdk`. Add `platform-tools` to PATH for `adb`:
  ```sh
  export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
  ```
- Test on a real phone over USB (enable USB debugging, then `adb devices`).
- The detector model isn't in git: after cloning run `cd app && ./gradlew downloadModel` (or export your own, stage 5).
- The default build is the Play build with multiplayer (beta, Google Play services Nearby, *Share this game* / *Join a game*; spec in [docs/dev/MULTI_DEVICE.md](MULTI_DEVICE.md)). `./gradlew -Pscan7.foss=true …` builds without Play services and without multiplayer (F-Droid).
- Releases (signing, versioning, Play / GitHub / F-Droid) are described in [docs/dev/RELEASE.md](RELEASE.md).

## Input data
Photos are **not in git** because they are large and some are private. A local `ml/data/manifest.csv` (not in git) lists every file with its original camera filename. Back up `ml/data/` separately.

The folders are listed below. File names matter in `cards_flat/`.

| Folder | Content |
|---|---|
| `ml/data/cards_flat/` | One straight-on photo per card on a dark background, named by class: `00.jpg` … `12.jpg`, `plus2.jpg`, `plus4.jpg`, `plus6.jpg`, `plus8.jpg`, `plus10.jpg`, `x2.jpg`, `freeze.jpg`, `flip3.jpg`, `second_chance.jpg`, `back.jpg`. Extra photos of the same class: `07_2.jpg`, `07_3.jpg`, … |
| `ml/data/cards_flat_vengeance/` | Same for Flip 7: With a Vengeance (German "Voll fies!"), classes from `ml/config/cards_vengeance.yaml`: `13`, `lucky13`, `unlucky7`, `zero`, `div2`, `minus2` … `minus10`, `just_one_more`, `swap`, `steal`, `discard`, `flip4` (+ `flip4_2`), `back`; 1–12 still missing. Raw shots (incl. table scenes) in `ml/data/vengeance_raw/` |
| `ml/data/backgrounds/` | 30–50 photos of tables, floors, fabrics, desk mats, with no cards |
| `ml/data/real_photos/train/` | Real game photos for fine-tuning (to be labeled) |
| `ml/data/real_photos/test/` | Real game photos for evaluation only. **Never train on these.** |
| `ml/data/app_scans/` | Raw frames saved by the app ("Save scans for training"), pulled with `scripts/import_app_scans.py`, which copies them into `real_photos/train|test` |
| `ml/data/unused/` | Kept for reference, not used (Flip 7 box shots, 12 on light background) |

## Stages
| # | Stage | Command | Status |
|---|---|---|---|
| 1 | Card cutouts | `uv run python scripts/make_cutouts.py` | done |
| 2 | Synthetic data | `cd ml && uv run python scripts/gen_synth.py --count 5000` (preview: `scripts/preview_synth.py`) | done |
| 3 | Real photo labeling | `ml/labeling/start.sh`, see [ml/labeling/README.md](../../ml/labeling/README.md) | done |
| 4 | Training & evaluation | `uv run python scripts/train.py …` (see AGENTS.md) | done (shipped: ft_v5 @ 800 px) |
| 5 | Export to LiteRT | `cd ml && uv run scripts/export.py <checkpoint>` (own env, not `uv run python`; installs into app assets, model not in git) | done (app ships fp32; int8 kept for CPU-only devices) |
| 6 | Android app | `cd app && ./gradlew installDebug` | debug screens + live detector on GPU; game flow pending (APP_FLOW.md) |
| 7 | Scoring logic | `cd app && ./gradlew testDebugUnitTest` | done |

## Stage 1: card cutouts
```sh
cd ml
uv run python scripts/make_cutouts.py            # add --measure to re-measure aspect / corner radius, --debug for detection overlays
uv run python scripts/make_cutouts.py --config config/cards_vengeance.yaml   # With a Vengeance -> out/cutouts_vengeance
uv run python scripts/gen_synth.py --config config/synth_vengeance.yaml --count 40 --out out/synth_vengeance_sample   # its synth data (base: synth.yaml)
```
- With a Vengeance has its own cards config (`paths:` = src / extra / out dirs; `cutouts.chroma` for the navy back, whose edge is as dark as the mat) and synth config (`cards_config`, `cutouts:` dir list with per-dir class filter + banner word area, `out`). Until Vengeance 1–12 are photographed, the Flip 7 cutouts of 1–12 stand in.
- The input is `ml/data/cards_flat/*.jpg`, one photo per card on the dark mat. The file stem is the class (`<class>_<n>.jpg` is an extra photo of the same class).
- The script writes upright, perspective-corrected RGBA cutouts (600×832 px) with an anti-aliased rounded-corner alpha to `ml/out/cutouts/<stem>.png`. It trims a few px off each edge so no mat remains and leaves the colors as photographed. `ml/out/cutouts/contact_sheet.jpg` shows all cutouts on a checkerboard with the banner box outlined in magenta on number cards.
- To find a card, the script thresholds the HSV brightness (Otsu), takes the convex hull of the largest blob, fits a line to the straight middle part of each edge, intersects these lines for the 4 sharp corners and computes the homography.
- `ml/config/cards.yaml` holds the fixed class list (ids 0–22 = YOLO class order), measured card geometry (aspect w/h 0.721, corner radius 0.042 × width), the word-banner box on number cards (`[x0, y0, x1, y1]` as fractions of card size), and per-photo rotation overrides for photos where the card isn't upright (`flip3: 90`, `plus8: 180`, `second_chance: 180`). Add an entry there when a new photo comes out rotated.
