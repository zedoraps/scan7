# scan7

Android app that scans and scores Flip 7 cards on-device. See [docs/BRIEF.md](docs/BRIEF.md) for the full plan.

## Layout
- `ml/` – Python: card cutouts, synthetic data, training, export
- `app/` – Android app (Kotlin + Jetpack Compose) — not started yet
- `docs/` – project brief and notes

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

## Input data
Photos are **not in git**: they're large, and the `PRIVATE_*` files show personal info. `ml/data/manifest.csv` lists every file with its original camera filename and notes. Back up `ml/data/` separately.

Folders (file names matter for `cards_flat/`):

| Folder | Content |
|---|---|
| `ml/data/cards_flat/` | One straight-on photo per card on a dark background, named by class: `00.jpg` … `12.jpg`, `plus2.jpg`, `plus4.jpg`, `plus6.jpg`, `plus8.jpg`, `plus10.jpg`, `x2.jpg`, `freeze.jpg`, `flip3.jpg`, `second_chance.jpg`, `back.jpg`. Extra photos of the same class: `07_2.jpg`, `07_3.jpg`, … |
| `ml/data/backgrounds/` | 30–50 photos of tables, floors, fabrics, desk mats — no cards |
| `ml/data/real_photos/train/` | Real game photos for fine-tuning (to be labeled) |
| `ml/data/real_photos/test/` | Real game photos for evaluation only — **never train on these** |
| `ml/data/unused/` | Kept for reference, not used (Flip 7 box shots, 12 on light background) |

## Stages
| # | Stage | Command | Status |
|---|---|---|---|
| 1 | Card cutouts | — | waiting for photos |
| 2 | Synthetic data | — | |
| 3 | Real photo labeling | — | |
| 4 | Training & evaluation | — | |
| 5 | Export to LiteRT | — | |
| 6 | Android app | — | |
| 7 | Scoring logic | — | |
