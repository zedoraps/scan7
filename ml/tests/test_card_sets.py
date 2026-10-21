"""Card-set config handling of make_cutouts.py / gen_synth.py (Flip 7 defaults + Vengeance).
Run: cd ml && uv run python -m unittest discover -s tests"""
import sys
import tempfile
import unittest
from pathlib import Path

ML = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ML / "scripts"))
import gen_synth as gs  # noqa: E402
import make_cutouts as mc  # noqa: E402


class MakeCutoutsPathsTest(unittest.TestCase):
    def test_flip7_defaults(self):
        self.assertEqual(mc.resolve_paths(mc.load_config()), (mc.SRC, mc.EXTRA, mc.OUT))

    def test_config_paths_and_cli_override(self):
        cfg = mc.load_config(ML / "config" / "cards_vengeance.yaml")
        src, extra, out = mc.resolve_paths(cfg)
        self.assertEqual((src, extra, out), (ML / "data/cards_flat_vengeance", ML / "data/cards_extra_vengeance", ML / "out/cutouts_vengeance"))
        src, extra, out = mc.resolve_paths(cfg, out="out/x", extra="data/cards_extra")
        self.assertEqual((extra, out), (ML / "data/cards_extra", ML / "out/x"))
        self.assertIsNone(mc.resolve_paths({}, extra="")[1])


class SynthConfigTest(unittest.TestCase):
    def test_base_merge_replaces_whole_sections(self):
        cfg = gs.load_synth_config(ML / "config" / "synth_vengeance.yaml")
        base = gs.load_yaml(gs.SYNTH_CONFIG)
        # Its own variants (English looks) replace Flip 7's whole section.
        self.assertEqual(cfg["variants"]["weights"]["back_en_render"], 0.4)
        self.assertNotIn("00_en_rainbow", cfg["variants"]["weights"])
        self.assertEqual(cfg["row"], base["row"])
        self.assertNotIn("base", cfg)
        self.assertEqual(gs.cards_config_path(cfg), ML / "config" / "cards_vengeance.yaml")
        self.assertEqual(gs.cards_config_path(base), gs.CARDS_CONFIG)

    def test_vengeance_classes(self):
        classes = gs.load_yaml(ML / "config" / "cards_vengeance.yaml")["classes"]
        self.assertEqual(len(classes), 28)
        self.assertEqual(len(set(classes)), 28)
        self.assertNotIn("00", classes)
        self.assertEqual(classes[:13], [f"{i:02d}" for i in range(1, 14)])
        self.assertEqual(classes[-1], "back")

    def test_cutout_dirs_with_class_filter(self):
        with tempfile.TemporaryDirectory() as t:
            a, b = Path(t, "a"), Path(t, "b")
            a.mkdir(), b.mkdir()
            for n in ("13", "back", "zero"):
                (a / f"{n}.png").touch()
            for n in ("01", "01_2", "00", "back", "plus2"):
                (b / f"{n}.png").touch()
            (b / "sources.yaml").write_text("'01': data/cards_flat/01.jpg\n'01_2': data/cards_extra/01_x.png\n")
            cfg = {"cutouts": [{"dir": str(a), "text_inner": [0, 0.7, 1, 0.8]}, {"dir": str(b), "classes": ["01"]}],
                   "variants": {"weights": {"01_x": 0.25}}}
            classes = ["01", "13", "zero", "back"]
            files = gs.cutout_files(classes, cfg)
            self.assertEqual([f.name for f in files["01"]], ["01.png", "01_2.png"])
            self.assertEqual([f.parent.name for f in files["back"]], ["a"])  # b's back is filtered out
            probs, inner = gs.variant_table(classes, cfg)
            self.assertEqual(list(probs["01"]), [0.75, 0.25])
            self.assertEqual(inner["13"], [[0, 0.7, 1, 0.8]])
            self.assertEqual(inner["01"], [None, None])
            with self.assertRaises(ValueError):  # unfiltered dir with a class not in the list
                gs.cutout_files(classes, {"cutouts": [str(b)]})


if __name__ == "__main__":
    unittest.main()
