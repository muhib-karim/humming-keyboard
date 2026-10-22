"""Tests for emulator_metrics.py. Run: python3 -m unittest scripts/test_emulator_metrics.py"""
import json
import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import emulator_metrics  # noqa: E402

AM_START = """Starting: Intent { cmp=com.humming.keyboard/dev.patrickgold.florisboard.SettingsLauncherAlias }
Status: ok
LaunchState: COLD
Activity: com.humming.keyboard/dev.patrickgold.florisboard.app.FlorisAppActivity
TotalTime: 900
WaitTime: 905
Complete
Status: ok
LaunchState: COLD
TotalTime: 700
WaitTime: 702
Complete
Status: ok
LaunchState: COLD
TotalTime: 650
Complete
Status: ok
LaunchState: WARM
TotalTime: 120
Complete
"""

MODEL = {"model": "fast", "download_ms": 41000, "load_ms": [1800, 1200, 1100], "warmup_decode_ms": 150,
         "threads": 4, "abi": "x86_64", "sdk": 34}


class EmulatorMetricsTest(unittest.TestCase):
    def test_cold_start_counts_only_cold_launches(self):
        c = emulator_metrics.cold_start(AM_START)
        self.assertEqual([900, 700, 650], c["values"])
        self.assertEqual(700, c["median"])
        self.assertEqual((650, 900), (c["min"], c["max"]))
        self.assertEqual(3, c["n"])

    def test_even_count_median_is_the_mean_of_the_middle_two(self):
        self.assertEqual(800, emulator_metrics.median([900, 700, 650, 1000]))

    def test_no_launches_is_an_error(self):
        with self.assertRaises(ValueError):
            emulator_metrics.cold_start("Status: ok\nComplete\n")

    def test_model_load_first_and_repeat(self):
        m = emulator_metrics.model_load(MODEL)
        self.assertEqual(1800, m["first_load_ms"])
        self.assertEqual(1150, m["repeat_load_median_ms"])
        self.assertEqual(150, m["warmup_decode_ms"])

    def test_cli_writes_labelled_json_and_markdown(self):
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "cold_start.txt"), "w") as f:
                f.write(AM_START)
            with open(os.path.join(d, "model_load.json"), "w") as f:
                json.dump(MODEL, f)
            out = os.path.join(d, "metrics.json")
            r = subprocess.run([sys.executable, "-I", os.path.join(os.path.dirname(__file__), "emulator_metrics.py"),
                                d, out, "--image", "system-images;android-34;google_apis;x86_64",
                                "--date", "2026-10-22", "--run", "123", "--commit", "abc1234"],
                               capture_output=True, text=True, check=True)
            with open(out) as f:
                j = json.load(f)
            self.assertEqual("system-images;android-34;google_apis;x86_64", j["image"])
            self.assertEqual("2026-10-22", j["date"])
            self.assertEqual(700, j["cold_start_ms"]["median"])
            self.assertEqual(1800, j["model_load"]["first_load_ms"])
            self.assertIn("| Cold start, median of 3 |", r.stdout)

    def test_missing_model_file_is_reported_not_invented(self):
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "cold_start.txt"), "w") as f:
                f.write(AM_START)
            j = emulator_metrics.collect(d, image="img", date="2026-10-22")
            self.assertIsNone(j["model_load"])
            self.assertIn("not measured", emulator_metrics.markdown(j))


if __name__ == "__main__":
    unittest.main()
