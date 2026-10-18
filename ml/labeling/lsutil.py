"""Shared helpers for the scan7 Label Studio scripts (stdlib only + PyYAML from the ml venv)."""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ML_DIR = HERE.parent
DATA_ROOT = ML_DIR / "data"  # = LABEL_STUDIO_LOCAL_FILES_DOCUMENT_ROOT
CARDS_YAML = ML_DIR / "config" / "cards.yaml"
CREDS = HERE / "ls_data" / "credentials.txt"
LABEL_CONFIG = HERE / "label_config.xml"

# Project title -> photo folder (absolute, must be inside DATA_ROOT).
PROJECTS = {
    "train": ("scan7 train", DATA_ROOT / "real_photos" / "train"),
    "test": ("scan7 test", DATA_ROOT / "real_photos" / "test"),
}
IMAGE_REGEX = r".*\.(jpe?g|JPE?G|png|PNG)$"
# Must match the name= attributes in label_config.xml.
FROM_NAME = "label"
TO_NAME = "image"
HINT_NAME = "hint"  # TextArea with hints for the labeler (see app_scans.py)


def load_classes() -> list[str]:
    import yaml

    with open(CARDS_YAML) as f:
        classes = yaml.safe_load(f)["classes"]
    classes = [str(c) for c in classes]
    assert len(classes) == len(set(classes)), "duplicate class names in cards.yaml"
    return classes


def load_creds() -> dict[str, str]:
    if not CREDS.exists():
        raise SystemExit(f"{CREDS} not found - run ml/labeling/start.sh once first.")
    out = {}
    for line in CREDS.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k] = v
    out["LS_URL"] = os.environ.get("LS_URL", out.get("LS_URL", "http://localhost:8080"))
    return out


class LS:
    """Minimal Label Studio REST client using the legacy API token."""

    def __init__(self):
        c = load_creds()
        self.url = c["LS_URL"].rstrip("/")
        self.token = c["LS_TOKEN"]

    def request(self, method: str, path: str, data=None, params=None, raw=False):
        url = self.url + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        body = None
        headers = {"Authorization": f"Token {self.token}"}
        if data is not None:
            body = json.dumps(data).encode()
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=body, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=300) as r:
                payload = r.read()
        except urllib.error.HTTPError as e:
            raise SystemExit(f"{method} {path} -> HTTP {e.code}: {e.read().decode(errors='replace')[:2000]}")
        except urllib.error.URLError as e:
            raise SystemExit(f"Cannot reach Label Studio at {self.url} ({e.reason}). Is start.sh running?")
        if raw:
            return payload
        return json.loads(payload) if payload else None

    def get(self, path, **params):
        return self.request("GET", path, params=params or None)

    def post(self, path, data=None):
        return self.request("POST", path, data=data if data is not None else {})

    def patch(self, path, data):
        return self.request("PATCH", path, data=data)

    def delete(self, path):
        return self.request("DELETE", path)

    def projects(self) -> list[dict]:
        r = self.get("/api/projects", page_size=1000)
        return r["results"] if isinstance(r, dict) else r

    def project_by_title(self, title: str) -> dict | None:
        matches = [p for p in self.projects() if p["title"] == title]
        if len(matches) > 1:
            raise SystemExit(f"More than one project titled {title!r}; delete the duplicates in the UI.")
        return matches[0] if matches else None

    def tasks(self, project_id: int) -> list[dict]:
        out, page = [], 1
        while True:
            r = self.get("/api/tasks", project=project_id, page=page, page_size=500)
            batch = r["tasks"] if isinstance(r, dict) else r
            out += batch
            if len(batch) < 500:
                return out
            page += 1


def task_image_relpath(task: dict) -> str:
    """Path of the task's image relative to DATA_ROOT, from a local-files URL
    like /data/local-files/?d=real_photos/train/x.jpg"""
    url = task["data"][TO_NAME]
    q = urllib.parse.urlparse(url).query
    d = urllib.parse.parse_qs(q).get("d")
    if not d:
        raise ValueError(f"task {task.get('id')}: not a local-files URL: {url}")
    return d[0]
