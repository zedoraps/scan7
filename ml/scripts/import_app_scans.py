"""Import "Save scans for training" frames from the phone into the real-photo dataset.

    uv run python scripts/import_app_scans.py                  # pull from the connected phone (adb)
    uv run python scripts/import_app_scans.py --from-dir ~/Downloads/scans   # or a folder / unzipped export
    uv run python scripts/import_app_scans.py --no-prelabel    # skip model pre-labels

Steps:
1. Copy new scan7_*.jpg (+ .json sidecars) into data/app_scans/ (raw archive, never edited).
2. Split new scans deterministically (hash of the file name) ~80/20 into data/real_photos/train and
   data/real_photos/test as app_<name>.jpg, and append them to data/manifest.csv.
3. Sync the Label Studio projects (Label Studio must be running: ml/labeling/start.sh).
4. Pre-label only the NEW TRAIN images with the newest model and upload the predictions.
   The test images are never pre-labeled.
"""
import argparse
import csv
import hashlib
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ML = Path(__file__).resolve().parent.parent
DATA = ML / "data"
RAW = DATA / "app_scans"
ADB = Path.home() / "Library/Android/sdk/platform-tools/adb"
PHONE_DIRS = ["/sdcard/Pictures/Scan7 Training", "/sdcard/Documents/Scan7 Training"]
TEST_PERCENT = 20


def phone_serial() -> str:
    out = subprocess.run([str(ADB), "devices"], capture_output=True, text=True, check=True).stdout
    devices = [l.split()[0] for l in out.splitlines()[1:] if l.strip().endswith("device")]
    phones = [d for d in devices if not d.startswith("emulator-")]
    if not phones:
        sys.exit("No phone found via adb. Turn on Wireless debugging on the phone (same Wi-Fi) and retry, "
                 "or export the 'Scan7 Training' folder and use --from-dir.")
    # The same phone can show up twice (ip:port and mDNS name); any entry works.
    return phones[0]


def pull_from_phone(dest: Path) -> None:
    serial = phone_serial()
    for remote in PHONE_DIRS:
        res = subprocess.run([str(ADB), "-s", serial, "pull", remote, str(dest)], capture_output=True, text=True)
        if res.returncode != 0 and "does not exist" not in res.stderr:
            print(f"  warning: adb pull {remote}: {res.stderr.strip()}")


def newest_weights() -> Path:
    candidates = sorted((ML / "runs").glob("*/weights/best.pt"), key=lambda p: p.stat().st_mtime, reverse=True)
    snapshots = sorted((ML / "out/snapshots").glob("*.pt"), key=lambda p: p.stat().st_mtime, reverse=True)
    for p in candidates + snapshots:
        return p
    sys.exit("No trained weights found.")


def split_for(name: str) -> str:
    h = int(hashlib.sha1(name.encode()).hexdigest(), 16) % 100
    return "test" if h < TEST_PERCENT else "train"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--from-dir", type=Path, help="folder with scan7_*.jpg/.json instead of pulling via adb")
    ap.add_argument("--no-prelabel", action="store_true")
    ap.add_argument("--weights", type=Path, help="model for pre-labels (default: newest best.pt)")
    args = ap.parse_args()

    RAW.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp)
        if args.from_dir:
            src = args.from_dir.expanduser()
        else:
            pull_from_phone(src)
        found = sorted(p for p in src.rglob("scan7_*") if p.suffix.lower() in {".jpg", ".json"})
        new_images = []
        for p in found:
            target = RAW / p.name
            if target.exists():
                continue
            shutil.copy2(p, target)
            if p.suffix.lower() == ".jpg":
                new_images.append(target)
    print(f"{len(new_images)} new scans ({len(found)} files found)")
    if not new_images:
        return

    manifest = DATA / "manifest.csv"
    rows, new_train = [], []
    for img in new_images:
        split = split_for(img.name)
        rel = f"real_photos/{split}/app_{img.name}"
        dst = DATA / rel
        if dst.exists():
            continue
        shutil.copy2(img, dst)
        rows.append({"path": rel, "source": f"app_scans/{img.name}", "notes": "app scan (Save scans for training)"})
        if split == "train":
            new_train.append(dst)
    with open(manifest, newline="") as f:
        existing = list(csv.DictReader(f))
    with open(manifest, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["path", "source", "notes"])
        w.writeheader()
        w.writerows(sorted(existing + rows, key=lambda r: r["path"]))
    print(f"  train +{len(new_train)}, test +{len(rows) - len(new_train)} (manifest updated)")

    run = lambda *cmd: subprocess.run(cmd, cwd=ML.parent, check=True)
    run("uv", "run", "--project", "ml", "python", "ml/labeling/setup_projects.py")

    if args.no_prelabel or not new_train:
        return
    weights = args.weights or newest_weights()
    print(f"Pre-labeling {len(new_train)} new train images with {weights.relative_to(ML)}")
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "src"
        src.mkdir()
        for p in new_train:
            (src / p.name).symlink_to(p)
        out = Path(tmp) / "pred"
        run("uv", "run", "--project", "ml", "python", "ml/scripts/predict.py", str(weights), str(src),
            "--out", str(out), "--device", "cpu")
        run("uv", "run", "--project", "ml", "python", "ml/labeling/import_predictions.py", str(out / "labels"),
            "--model-version", weights.parent.parent.name if weights.parent.name == "weights" else weights.stem)


if __name__ == "__main__":
    main()
