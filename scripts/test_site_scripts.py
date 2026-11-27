"""Tests for assemble_site.py and check_site.py. Run: python3 -m unittest scripts/test_site_scripts.py"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import assemble_site  # noqa: E402
import check_site  # noqa: E402


class SiteScriptsTest(unittest.TestCase):
    def test_assembled_site_passes_all_checks(self):
        with tempfile.TemporaryDirectory() as d:
            out = os.path.join(d, "site")
            count = assemble_site.assemble(out)
            self.assertGreater(count, 20)
            pages, problems = check_site.check_dir(out)
            self.assertGreaterEqual(pages, 3)
            self.assertEqual(problems, [], problems)

    def test_check_site_catches_missing_alt_external_script_and_broken_img(self):
        with tempfile.TemporaryDirectory() as d:
            html = (
                '<!doctype html><html><head><title>Bad</title>'
                '<script src="https://evil.invalid/a.js"></script></head>'
                '<body><img src="missing.png"></body></html>'
            )
            with open(os.path.join(d, "index.html"), "w", encoding="utf-8") as fh:
                fh.write(html)
            _, problems = check_site.check_dir(d)
            joined = "\n".join(problems)
            self.assertIn("another origin", joined)
            self.assertIn("img without alt", joined)
            self.assertIn("does not exist", joined)


if __name__ == "__main__":
    unittest.main()
