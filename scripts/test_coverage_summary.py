"""Tests for coverage_summary.py (JaCoCo XML -> per-package line coverage). Run: python3 -m unittest scripts/test_coverage_summary.py"""
import json
import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import coverage_summary  # noqa: E402

XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
<report name="debug">
  <package name="com/humming/keyboard">
    <class name="com/humming/keyboard/A"><counter type="LINE" missed="9" covered="99"/></class>
    <counter type="INSTRUCTION" missed="10" covered="30"/>
    <counter type="LINE" missed="1" covered="3"/>
  </package>
  <package name="com/humming/keyboard/predict">
    <counter type="LINE" missed="5" covered="5"/>
  </package>
  <package name="dev/patrickgold/florisboard">
    <counter type="LINE" missed="90" covered="10"/>
  </package>
  <counter type="LINE" missed="96" covered="18"/>
</report>
"""


class CoverageSummaryTest(unittest.TestCase):
    def test_package_lines_use_the_package_counter_not_class_counters(self):
        s = coverage_summary.summarise(XML)
        self.assertEqual({"covered": 3, "missed": 1, "pct": 75.0}, s["packages"]["com/humming/keyboard"])

    def test_report_total(self):
        s = coverage_summary.summarise(XML)
        self.assertEqual({"covered": 18, "missed": 96, "pct": 15.8}, s["total"])

    def test_prefix_groups_subpackages(self):
        s = coverage_summary.summarise(XML, prefix="com/humming/keyboard")
        # 3 + 5 covered, 1 + 5 missed
        self.assertEqual({"covered": 8, "missed": 6, "pct": 57.1}, s["prefix"])

    def test_empty_report_is_zero_not_a_crash(self):
        s = coverage_summary.summarise('<report name="x"></report>')
        self.assertEqual({"covered": 0, "missed": 0, "pct": 0.0}, s["total"])

    def test_cli_writes_json_and_markdown(self):
        with tempfile.TemporaryDirectory() as d:
            x = os.path.join(d, "r.xml")
            with open(x, "w") as f:
                f.write(XML)
            out = os.path.join(d, "c.json")
            r = subprocess.run([sys.executable, "-I", os.path.join(os.path.dirname(__file__), "coverage_summary.py"),
                                x, "--prefix", "com/humming/keyboard", "--json", out],
                               capture_output=True, text=True, check=True)
            self.assertIn("| com/humming/keyboard (all) | 8 | 14 | 57.1 % |", r.stdout)
            with open(out) as f:
                self.assertEqual(57.1, json.load(f)["prefix"]["pct"])


if __name__ == "__main__":
    unittest.main()
