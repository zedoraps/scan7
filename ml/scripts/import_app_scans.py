"""Import "Save scans for training" frames from the phone into the real-photo dataset.

    uv run python scripts/import_app_scans.py                  # pull from the connected phone (adb)
    uv run python scripts/import_app_scans.py --from-dir ~/Downloads/scans   # or a folder / unzipped export
    uv run python scripts/import_app_scans.py --no-prelabel    # skip model pre-labels

Steps:
1. Copy new scan7_*.jpg (+ .json sidecars) into data/app_scans/ (raw archive, never edited).
2. Split new scans deterministically (hash of the file name) ~80/20 into data/real_photos/train and
   data/real_photos/test as app_<name>.jpg, and append them to data/manifest.csv.
3. Sync the Label Studio projects (Label Studio must be running: ml/labeling/start.sh).
4. Compare the app's detections with the cards the user confirmed (ml/labeling/app_scans.py):
   train: exact match -> accepted as the annotation automatically; otherwise a pre-label with
   relabelled boxes and a hint ("Missing: 07 - draw the box(es)", "Remove: 12 ...").
   test: never the model's boxes - only a hint listing the confirmed cards.
"""
import argparse
import csv
import json
from collections import Counter
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


def upload_reviews(images: list[Path], review_all: bool = False) -> None:
    """Annotations / pre-labels / hints in Label Studio for newly imported app scans."""
    sys.path.insert(0, str(ML / "labeling"))
    from app_scans import load_sidecar, reconcile, review_all_hint, test_hint, to_ls_results
    from lsutil import FROM_NAME, HINT_NAME, LS, PROJECTS, TO_NAME, task_image_relpath

    ls = LS()
    counts = Counter()
    for split in ("train", "test"):
        p = ls.project_by_title(PROJECTS[split][0])
        tasks = {Path(task_image_relpath(t)).stem: t for t in ls.tasks(p["id"])}
        for img in images:
            if img.parent.name != split:
                continue
            task = tasks.get(img.stem)
            sidecar = RAW / (img.stem.removeprefix("app_") + ".json")
            if task is None or not sidecar.exists():
                counts["no task/sidecar"] += 1
                continue
            meta = json.loads(sidecar.read_text())
            w, h = meta["image"]["width"], meta["image"]["height"]
            boxes, truth = load_sidecar(sidecar)
            if split == "test":
                res = to_ls_results([], test_hint(truth), w, h, FROM_NAME, TO_NAME, HINT_NAME)
                ls.post("/api/predictions", {"task": task["id"], "result": res, "model_version": "app-hint"})
                counts["test hint"] += 1
                continue
            if review_all:
                res = to_ls_results(boxes, review_all_hint(boxes, truth), w, h, FROM_NAME, TO_NAME, HINT_NAME)
                ls.post("/api/predictions", {"task": task["id"], "result": res, "model_version": "app-scan"})
                counts["to review"] += 1
                continue
            review = reconcile(boxes, truth)
            res = to_ls_results(review.boxes, review.hint, w, h, FROM_NAME, TO_NAME, HINT_NAME)
            if review.auto_accept:
                ls.post(f"/api/tasks/{task['id']}/annotations", {"result": res})
            else:
                ls.post("/api/predictions", {"task": task["id"], "result": res, "model_version": "app-scan"})
            counts[review.status] += 1
    print("Label Studio:", ", ".join(f"{k} {v}" for k, v in sorted(counts.items())) or "nothing to upload")
    if counts["match"]:
        print(f"  {counts['match']} train scans matched the confirmed cards and were accepted automatically.")


def split_for(name: str) -> str:
    h = int(hashlib.sha1(name.encode()).hexdigest(), 16) % 100
    return "test" if h < TEST_PERCENT else "train"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--from-dir", type=Path, help="folder with scan7_*.jpg/.json instead of pulling via adb")
    ap.add_argument("--no-prelabel", action="store_true", help="only copy + sync, no annotations/hints")
    ap.add_argument("--review-all", action="store_true",
                    help="app confirmations may be wrong: nothing is auto-accepted, every train scan gets the\n                         model boxes + a hint with both card lists")
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
    if not args.no_prelabel:
        upload_reviews([(DATA / r["path"]) for r in rows], review_all=args.review_all)

if __name__ == "__main__":
    main()
