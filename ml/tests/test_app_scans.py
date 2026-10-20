"""Run: cd ml && uv run python -m unittest discover -s tests"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "labeling"))
import json, tempfile  # noqa: E402
from app_scans import Box, load_sidecar, reconcile, review_all_hint, test_hint  # noqa: E402


def box(label, score=0.9, x=0.1):
    return Box(label, score, x, 0.1, x + 0.1, 0.3)


class ReconcileTest(unittest.TestCase):
    def test_match_is_auto_accepted(self):
        r = reconcile([box("07"), box("12"), box("freeze")], ["12", "07", "freeze"])
        self.assertEqual(r.status, "match")
        self.assertTrue(r.auto_accept)
        self.assertEqual(r.hint, "")

    def test_single_swap_is_relabelled(self):
        r = reconcile([box("01", 0.6), box("12")], ["07", "12"])
        self.assertEqual(r.status, "relabelled")
        self.assertFalse(r.auto_accept)
        self.assertEqual(sorted(b.label for b in r.boxes), ["07", "12"])
        self.assertIn("01 -> 07", r.hint)
        self.assertIn("auto-relabelled from 01", next(b.note for b in r.boxes if b.label == "07"))

    def test_lowest_score_box_of_a_duplicate_is_relabelled(self):
        # Model saw two 12s, the user confirmed a 12 and a 1: the weaker 12 becomes the 1.
        r = reconcile([box("12", 0.95, 0.1), box("12", 0.40, 0.5)], ["12", "01"])
        weak = min(r.boxes, key=lambda b: b.score)
        self.assertEqual(weak.label, "01")
        self.assertEqual(r.status, "relabelled")

    def test_missing_card(self):
        r = reconcile([box("07")], ["07", "plus6"])
        self.assertEqual(r.status, "mismatch")
        self.assertEqual(r.missing, ["plus6"])
        self.assertIn("Missing: plus6", r.hint)

    def test_extra_box_is_flagged(self):
        r = reconcile([box("12", 0.9), box("12", 0.5), box("07")], ["12", "07"])
        self.assertEqual(r.extra, ["12"])
        self.assertIn("Remove: 12", r.hint)
        flagged = [b for b in r.boxes if b.note]
        self.assertEqual(len(flagged), 1)
        self.assertEqual(flagged[0].score, 0.5)

    def test_two_different_missing_cards_are_not_guessed(self):
        r = reconcile([box("01"), box("08")], ["07", "plus6"])
        self.assertEqual(sorted(b.label for b in r.boxes), ["01", "08"])
        self.assertEqual(r.missing, ["07", "plus6"])
        self.assertEqual(r.extra, ["01", "08"])

    def test_unconfirmed(self):
        r = reconcile([box("07")], None)
        self.assertEqual(r.status, "unconfirmed")
        self.assertFalse(r.auto_accept)

    def test_test_hint_lists_confirmed_cards(self):
        self.assertIn("07, 12", test_hint(["12", "07"]))

    def test_review_all_hint_shows_both_lists(self):
        h = review_all_hint([box("01"), box("12")], ["07", "12"])
        self.assertIn("Model saw: 01, 12", h)
        self.assertIn("may be wrong): 07, 12", h)
        self.assertIn("differs", h)

    def test_excluded_cards_count_as_truth(self):
        meta = {"detections": [{"label": "07", "score": 0.9, "box": {"left": 0, "top": 0, "right": .1, "bottom": .1}},
                               {"label": "07", "score": 0.8, "box": {"left": .5, "top": 0, "right": .6, "bottom": .1}}],
                "outcome": {"cards": ["07"], "ignored": [], "excluded": ["07"]}}
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
            json.dump(meta, f)
        boxes, truth = load_sidecar(Path(f.name))
        self.assertEqual(reconcile(boxes, truth).status, "match")

    def test_auto_excluded_cards_are_real_cards(self):
        # The app left a neighbour's 12 out automatically ("auto_excluded"); it stays in outcome.excluded
        # (the user didn't bring it back), so it is still a labelled card. A card brought back is in cards.
        det = lambda label, x, auto: {"label": label, "score": 0.9, "box": {"left": x, "top": 0, "right": x + .1, "bottom": .1},
                                      "from_tracking": False, "excluded": auto and label == "12", "auto_excluded": auto}
        meta = {"detections": [det("07", 0.3, False), det("12", 0.0, True), det("05", 0.9, True)],
                "outcome": {"cards": ["07", "05"], "ignored": [], "excluded": ["12"]}}
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
            json.dump(meta, f)
        boxes, truth = load_sidecar(Path(f.name))
        self.assertEqual(sorted(b.label for b in boxes), ["05", "07", "12"])
        self.assertEqual(reconcile(boxes, truth).status, "match")


if __name__ == "__main__":
    unittest.main()
