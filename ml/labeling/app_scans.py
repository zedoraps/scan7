"""Compare what the app's detector saw with what the user confirmed, and turn it into Label Studio work.

Each app scan has a JSON sidecar (app/.../training/TrainingSample.kt) with the detector's boxes and the
cards the user confirmed (outcome.cards + outcome.ignored). `reconcile()` compares the two label
multisets:

- match              -> the boxes are accepted as the final annotation (no human review needed)
- one-to-one swaps   -> wrong boxes are relabelled to the confirmed card, sent for a quick check
- missing / extra    -> boxes kept as a pre-label, with a hint ("missing: 07 - draw its box",
                        "remove: 12 (probably a duplicate)") on the task and on the affected boxes
- not confirmed      -> plain pre-label

Test-split scans never get the detector's boxes: only a hint listing the confirmed cards, so the
labels stay independent of the model.
"""
from __future__ import annotations

import json
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class Box:
    label: str
    score: float
    left: float
    top: float
    right: float
    bottom: float
    note: str = ""  # shown on the region in Label Studio


@dataclass
class Review:
    status: str  # "match" | "relabelled" | "mismatch" | "unconfirmed"
    boxes: list[Box]
    hint: str
    missing: list[str] = field(default_factory=list)  # confirmed but not boxed (after relabelling)
    extra: list[str] = field(default_factory=list)  # boxed but not confirmed (after relabelling)

    @property
    def auto_accept(self) -> bool:
        return self.status == "match"


def load_sidecar(path: Path) -> tuple[list[Box], list[str] | None]:
    """Detector boxes and the confirmed labels (cards + ignored), or None if the scan wasn't confirmed."""
    d = json.loads(path.read_text())
    boxes = [Box(x["label"], float(x["score"]), x["box"]["left"], x["box"]["top"], x["box"]["right"],
                 x["box"]["bottom"]) for x in d.get("detections") or []]
    o = d.get("outcome")
    truth = None if o is None else list(o.get("cards") or []) + list(o.get("ignored") or [])
    return boxes, truth


def _fmt(labels) -> str:
    return ", ".join(sorted(labels))


def reconcile(boxes: list[Box], truth: list[str] | None) -> Review:
    boxes = [Box(**vars(b)) for b in boxes]
    if truth is None:
        return Review("unconfirmed", boxes, "Not confirmed in the app - check all boxes.")
    have, want = Counter(b.label for b in boxes), Counter(truth)
    if have == want:
        return Review("match", boxes, "")

    missing = want - have  # confirmed, not boxed
    extra = have - want  # boxed, not confirmed
    relabelled = []
    # Pair each extra box with a missing card: the lowest-confidence boxes of an extra label are the
    # likeliest mistakes. Unambiguous when there is exactly one extra and one missing card.
    extra_boxes = sorted((b for b in boxes if extra[b.label] > 0), key=lambda b: b.score)
    taken = Counter()
    for b in extra_boxes:
        if taken[b.label] >= extra[b.label] or not missing:
            continue
        new = sorted(missing.elements())[0] if len(missing) == 1 else None
        if new is None:
            break  # several different missing cards: don't guess which box is which
        relabelled.append(f"{b.label} -> {new}")
        b.note = f"auto-relabelled from {b.label} (app: {new}) - check"
        taken[b.label] += 1
        missing[new] -= 1
        missing = +missing
        b.label = new
    have = Counter(b.label for b in boxes)
    missing, extra = want - have, have - want

    parts = []
    if relabelled:
        parts.append("Relabelled: " + "; ".join(relabelled) + ".")
    if missing:
        parts.append(f"Missing: {_fmt(missing.elements())} - draw the box(es).")
    if extra:
        parts.append(f"Remove: {_fmt(extra.elements())} (not in the confirmed row, maybe a duplicate).")
        left = Counter(extra)
        for b in sorted(boxes, key=lambda b: b.score):
            if left[b.label] > 0 and not b.note:
                b.note = "not in the confirmed row - remove?"
                left[b.label] -= 1
    status = "relabelled" if relabelled and not missing and not extra else "mismatch"
    return Review(status, boxes, " ".join(parts), sorted(missing.elements()), sorted(extra.elements()))


def test_hint(truth: list[str] | None) -> str:
    if not truth:
        return "Label all cards by hand."
    return f"Cards confirmed in the app: {_fmt(truth)}. Label all of them by hand."


# --- Label Studio results -------------------------------------------------------------------------

def to_ls_results(boxes: list[Box], hint: str, width: int, height: int, from_name: str, to_name: str,
                  hint_name: str) -> list[dict]:
    res = []
    for i, b in enumerate(boxes):
        r = {
            "id": f"app{i}",
            "from_name": from_name,
            "to_name": to_name,
            "type": "rectanglelabels",
            "original_width": width,
            "original_height": height,
            "image_rotation": 0,
            "value": {"x": b.left * 100, "y": b.top * 100, "width": (b.right - b.left) * 100,
                      "height": (b.bottom - b.top) * 100, "rotation": 0, "rectanglelabels": [b.label]},
            "score": b.score,
        }
        if b.note:
            r["meta"] = {"text": [b.note]}
        res.append(r)
    if hint:
        res.append({"id": "apphint", "from_name": hint_name, "to_name": to_name, "type": "textarea",
                    "value": {"text": [hint]}})
    return res
