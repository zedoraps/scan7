# Real photo labeling (Label Studio)

Milestone 3: label the real photos in `ml/data/real_photos/{train,test}` with boxes for the
23 card classes and export them as a YOLO dataset.

Label Studio (tested with 1.23.2) is installed as an isolated uv tool, not in the ML venv:

```bash
uv tool install --python 3.12 label-studio
```

All Label Studio state (SQLite DB, exports, credentials) lives in `ml/labeling/ls_data/`
(gitignored). The photos are never copied or modified; Label Studio serves them read-only
from `ml/data` (Local Files storage).

## 1. Start

```bash
ml/labeling/start.sh          # http://localhost:8080, Ctrl+C to stop
```

The first start creates an admin user and writes `ml/labeling/ls_data/credentials.txt`
(`LS_EMAIL`, `LS_PASSWORD`, `LS_TOKEN`). Log in with that email and password.
Analytics and the update check are switched off; the server listens on 127.0.0.1 only.

## 2. Create projects / sync new photos (in a second terminal)

```bash
uv run --project ml python ml/labeling/setup_projects.py
```

Idempotent. It creates **scan7 train** (`real_photos/train`) and **scan7 test**
(`real_photos/test`) with the generated label config, and syncs both folders. Run it again
(or click *Settings > Cloud Storage > Sync Storage* in a project) after adding photos;
new files become new tasks, existing labels are kept. The test project is separate on
purpose: test labels must never end up in training.

The label config is generated from `ml/config/cards.yaml` (class order = YOLO ids):

```bash
uv run --project ml python ml/labeling/make_config.py   # then re-run setup_projects.py
```

## 3. Label

Open a project, click *Label All Tasks*. Select a class (click, or hotkey), then drag a box.

| Hotkey | Class | Hotkey | Class |
|---|---|---|---|
| `0`-`9` | `00`-`09` | `a s d f g` | `plus2 plus4 plus6 plus8 plus10` |
| `q` | `10` | `x` | `x2` |
| `i` | `11` | `z` | `freeze` |
| `l` | `12` | `t` | `flip3` |
| `c` | `second_chance` | `n` | `back` |

Rules (match the synthetic data):
- Draw **tight, axis-aligned boxes around the visible part** of each card (corners included,
  no table margin). For a rotated card the box encloses the whole rotated card.
- Face-down cards are `back`.
- **Skip cards that are more than ~70% hidden** (no box at all).
- Label every other card in the photo, including partly visible cards of neighbouring rows
  and cards cut off by the image edge (if at least ~30% is visible).
- A photo without any cards: just *Submit* with no boxes (exported as a background image
  only with `--include-unlabeled`).
- Submit with `Ctrl+Enter`. Zoom with the mouse wheel; use the hand (pan) tool to move around.

## 4. Export to YOLO

Label Studio must be running.

```bash
uv run --project ml python ml/labeling/export_yolo.py
```

Writes `ml/out/real/{train,test}/{images,labels}` and `ml/out/real/dataset.yaml`
(`val`/`test` = the real test set). Class ids come from `cards.yaml`, not from Label Studio's
own YOLO export. Images are symlinked; photos with an EXIF rotation tag are written as
upright copies instead, so the boxes (which Label Studio stores relative to the upright,
EXIF-applied image) match what Ultralytics loads. Only labeled tasks are exported;
`--include-unlabeled` also exports unlabeled photos as empty (background) images, `--copy`
copies instead of symlinking. The output folder is rebuilt on each run.

## 5. Pre-labeling with a model (later)

Once a synthetic-trained model exists, predict on the train photos and upload the boxes as
editable pre-labels:

```bash
uv run --project ml yolo predict model=ml/runs/.../best.pt source=ml/data/real_photos/train \
    save_txt=True save_conf=True project=ml/out/prelabel name=v1
uv run --project ml python ml/labeling/import_predictions.py ml/out/prelabel/v1/labels --model-version v1
```

Then open tasks and correct the boxes instead of drawing them. Add `--replace` to overwrite
predictions of the same model version, `--min-conf 0.25` to drop weak boxes.
Do not pre-label the test project (it would bias the test labels towards the model).

## Files

- `start.sh` - start the server (env, data dir, first-run admin user)
- `make_config.py` -> `label_config.xml` - labeling UI config from `cards.yaml`
- `setup_projects.py` - create projects + Local Files storage, sync
- `export_yolo.py` - annotations -> YOLO dataset
- `import_predictions.py` - YOLO predictions -> Label Studio pre-labels
- `lsutil.py` - shared REST helpers / paths
- `ls_data/` - runtime state, gitignored (delete it to start from scratch)
