# Development

Setup, data layout and the ML stages. Plan: [BRIEF.md](BRIEF.md). Coding agents: read [AGENTS.md](../../AGENTS.md) first (commands, hard rules, decisions, conventions).

## Branding
App name **Scan7**, dark "Deep Cyan" theme, Unbounded + DM Mono fonts. Brand kit and rules: [brand/BRAND.md](../../brand/BRAND.md); in the app see `ui/theme/` (`Scan7Theme`, `Scan7Theme.colors`, `Scan7Wordmark`). Font licenses: [docs/third_party/](../third_party/).

## Layout
- `ml/` – Python: card cutouts, synthetic data, training, export
- `app/` – Android app (Kotlin + Jetpack Compose)
- `docs/` – privacy policy, store listing, licences; `docs/dev/` – plan, specs, release checklist

## Setup

### ML environment
Requires [uv](https://docs.astral.sh/uv/). It fetches Python 3.12 automatically.

```sh
cd ml
uv sync                 # creates ml/.venv with Python 3.12 and installs deps
uv run python --version # → Python 3.12.x
```

Run any script with `uv run python scripts/<name>.py` from `ml/`.

### Android
- Android SDK lives at `~/Library/Android/sdk`. Add `platform-tools` to PATH for `adb`:
  ```sh
  export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
  ```
- Test on a real phone over USB (enable USB debugging, then `adb devices`).
- The detector model isn't in git: after cloning run `cd app && ./gradlew downloadModel` (or export your own, stage 5).
- The default build is the Play build with multiplayer (beta, Google Play services Nearby: *Share this game* / *Join a game*, spec [docs/dev/MULTI_DEVICE.md](MULTI_DEVICE.md)); `./gradlew -Pscan7.foss=true …` builds without Play services and without multiplayer (F-Droid).
- Releases (signing, versioning, Play / GitHub / F-Droid): [docs/dev/RELEASE.md](RELEASE.md).

## Input data
Photos are **not in git**: they are large and some are private. A local `ml/data/manifest.csv` (not in git) lists every file with its original camera filename. Back up `ml/data/` separately.

Folders (file names matter for `cards_flat/`):

| Folder | Content |
|---|---|
| `ml/data/cards_flat/` | One straight-on photo per card on a dark background, named by class: `00.jpg` … `12.jpg`, `plus2.jpg`, `plus4.jpg`, `plus6.jpg`, `plus8.jpg`, `plus10.jpg`, `x2.jpg`, `freeze.jpg`, `flip3.jpg`, `second_chance.jpg`, `back.jpg`. Extra photos of the same class: `07_2.jpg`, `07_3.jpg`, … |
| `ml/data/backgrounds/` | 30–50 photos of tables, floors, fabrics, desk mats — no cards |
| `ml/data/real_photos/train/` | Real game photos for fine-tuning (to be labeled) |
| `ml/data/real_photos/test/` | Real game photos for evaluation only — **never train on these** |
| `ml/data/app_scans/` | Raw frames saved by the app ("Save scans for training"), pulled with `scripts/import_app_scans.py`, which copies them into `real_photos/train|test` |
| `ml/data/unused/` | Kept for reference, not used (Flip 7 box shots, 12 on light background) |

## Stages
| # | Stage | Command | Status |
|---|---|---|---|
| 1 | Card cutouts | `uv run python scripts/make_cutouts.py` | done |
| 2 | Synthetic data | `cd ml && uv run python scripts/gen_synth.py --count 5000` (preview: `scripts/preview_synth.py`) | done |
| 3 | Real photo labeling | `ml/labeling/start.sh` — see [ml/labeling/README.md](../../ml/labeling/README.md) | done |
| 4 | Training & evaluation | `uv run python scripts/train.py …` (see AGENTS.md) | done (shipped: ft_v5 @ 800 px) |
| 5 | Export to LiteRT | `cd ml && uv run scripts/export.py <checkpoint>` (own env — not `uv run python`; installs into app assets, model not in git) | done (app ships fp32; int8 kept for CPU-only devices) |
| 6 | Android app | `cd app && ./gradlew installDebug` | debug screens + live detector on GPU; game flow pending (APP_FLOW.md) |
| 7 | Scoring logic | `cd app && ./gradlew testDebugUnitTest` | done |

## Stage 1 – Card cutouts
```sh
cd ml
uv run python scripts/make_cutouts.py            # add --measure to re-measure aspect / corner radius, --debug for detection overlays
```
- **Input:** `ml/data/cards_flat/*.jpg` — one photo per card on the dark mat. The file stem is the class (`<class>_<n>.jpg` = extra photo of the same class).
- **Output:** `ml/out/cutouts/<stem>.png` — upright, perspective-corrected RGBA cutouts (600×832 px) with an anti-aliased rounded-corner alpha; a few px are trimmed off each edge so no mat remains. Colors are left as photographed. `ml/out/cutouts/contact_sheet.jpg` shows all cutouts on a checkerboard with the banner box outlined in magenta on number cards.
- **How:** threshold the HSV brightness (Otsu) → convex hull of the largest blob → fit a line to the straight middle part of each edge → intersect them for the 4 sharp corners → homography.
- **Config:** `ml/config/cards.yaml` — the fixed class list (ids 0–22 = YOLO class order), measured card geometry (aspect w/h 0.721, corner radius 0.042 × width), the word-banner box on number cards (`[x0, y0, x1, y1]` as fractions of card size), and per-photo rotation overrides for photos where the card isn't upright (`flip3: 90`, `plus8: 180`, `second_chance: 180`). Add an entry there when a new photo comes out rotated.
