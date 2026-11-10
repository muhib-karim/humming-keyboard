"""Tests for the pure parts of verify_updater.py (no PyNaCl, no network). Run: python3 -m unittest scripts/test_verify_updater.py"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import verify_updater  # noqa: E402

OLD = "https://humming-keyboard.pages.dev/updates/Humming_1.8.0_amd64.AppImage"
SITE = "https://muhib-karim.github.io/humming-keyboard/updates"


class BundleUrlTest(unittest.TestCase):
    def test_without_a_base_the_feed_url_is_used(self):
        self.assertEqual(verify_updater.bundle_url(OLD), OLD)
        self.assertEqual(verify_updater.bundle_url(OLD, ""), OLD)

    def test_with_a_base_the_file_is_fetched_by_name_from_it(self):
        # The 1.8.0 feed lists a host the site does not deploy to: the check must read the copy the site serves.
        want = SITE + "/Humming_1.8.0_amd64.AppImage"
        self.assertEqual(verify_updater.bundle_url(OLD, SITE), want)
        self.assertEqual(verify_updater.bundle_url(OLD, SITE + "/"), want)

    def test_a_feed_already_on_the_base_is_unchanged(self):
        url = SITE + "/Humming_1.8.1_x64-setup.exe"
        self.assertEqual(verify_updater.bundle_url(url, SITE + "/"), url)

    def test_pynacl_is_not_needed_to_import_the_module(self):
        # quality.yml runs these tests without PyNaCl: it is imported inside the functions that verify.
        self.assertFalse(hasattr(verify_updater, "VerifyKey"))


if __name__ == "__main__":
    unittest.main()
