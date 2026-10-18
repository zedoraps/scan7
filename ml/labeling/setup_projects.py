"""Idempotently create/update the Label Studio projects for scan7 and sync their photos.

    uv run --project ml python ml/labeling/setup_projects.py

- "scan7 train" <- ml/data/real_photos/train
- "scan7 test"  <- ml/data/real_photos/test   (separate project: test labels never mix with train)

Each project gets label_config.xml and a Local Files storage pointing at its folder.
Re-running pushes the current label config and re-syncs storage, so photos added to the
folders later show up as new tasks (existing tasks/annotations are kept).
"""

from __future__ import annotations

import time

from lsutil import DATA_ROOT, IMAGE_REGEX, LABEL_CONFIG, LS, PROJECTS


def ensure_project(ls: LS, title: str, label_config: str, folder) -> dict:
    p = ls.project_by_title(title)
    desc = f"scan7 Flip 7 card labeling. Photos: ml/data/{folder.relative_to(DATA_ROOT)}"
    if p is None:
        p = ls.post("/api/projects", {"title": title, "description": desc, "label_config": label_config})
        print(f"[{title}] created project id={p['id']}")
    elif p.get("label_config", "").strip() != label_config.strip():
        p = ls.patch(f"/api/projects/{p['id']}", {"label_config": label_config})
        print(f"[{title}] updated label config (project id={p['id']})")
    else:
        print(f"[{title}] exists (project id={p['id']})")
    return p


def ensure_storage(ls: LS, project_id: int, title: str, folder) -> dict:
    storages = ls.get("/api/storages/localfiles", project=project_id)
    want = {
        "project": project_id,
        "title": f"{title} photos",
        "path": str(folder),
        "regex_filter": IMAGE_REGEX,
        "use_blob_urls": True,  # one task per image file
    }
    for s in storages:
        if s.get("path") == str(folder):
            if s.get("regex_filter") != IMAGE_REGEX or not s.get("use_blob_urls"):
                s = ls.patch(f"/api/storages/localfiles/{s['id']}", want)
            return s
    s = ls.post("/api/storages/localfiles", want)
    print(f"[{title}] created Local Files storage id={s['id']} -> {folder}")
    return s


def sync(ls: LS, storage_id: int, title: str):
    ls.post(f"/api/storages/localfiles/{storage_id}/sync")
    for _ in range(120):
        s = ls.get(f"/api/storages/localfiles/{storage_id}")
        if s.get("status") in ("completed", "failed") or s.get("status") is None:
            break
        time.sleep(1)
    if s.get("status") == "failed":
        raise SystemExit(f"[{title}] storage sync failed: {s.get('traceback') or s.get('meta')}")


def main():
    label_config = LABEL_CONFIG.read_text()
    ls = LS()
    for key, (title, folder) in PROJECTS.items():
        if not folder.is_dir():
            raise SystemExit(f"missing photo folder {folder}")
        p = ensure_project(ls, title, label_config, folder)
        s = ensure_storage(ls, p["id"], title, folder)
        sync(ls, s["id"], title)
        n_files = sum(1 for f in folder.iterdir() if f.suffix.lower() in (".jpg", ".jpeg", ".png"))
        tasks = ls.tasks(p["id"])
        n_done = sum(1 for t in tasks if t.get("total_annotations", 0) > 0)
        print(f"[{title}] synced: {len(tasks)} tasks ({n_files} image files in folder), {n_done} annotated")
        print(f"[{title}] {ls.url}/projects/{p['id']}/data")


if __name__ == "__main__":
    main()
