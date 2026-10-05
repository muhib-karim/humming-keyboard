package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    private val feed = """{"versionCode":1601,"versionName":"1.6.1","apk":"Humming-1.6.1.apk","notes":"Fixes"}"""

    @Test
    fun parsesTheFeed() {
        val r = UpdatePolicy.parseFeed(feed)
        assertEquals(1601, r.versionCode)
        assertEquals("1.6.1", r.versionName)
        assertEquals("Humming-1.6.1.apk", r.apk)
        assertEquals("Fixes", r.notes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesAPathOrAnotherFileInTheFeed() {
        UpdatePolicy.parseFeed("""{"versionCode":1601,"versionName":"1.6.1","apk":"../evil.apk"}""")
    }

    @Test
    fun onlyPlainApkNames() {
        assertTrue(UpdatePolicy.isSafeApkName("Humming-1.6.0.apk"))
        assertFalse(UpdatePolicy.isSafeApkName("https://example.com/Humming-1.6.0.apk"))
        assertFalse(UpdatePolicy.isSafeApkName("Humming-1.6.0.aab"))
        assertFalse(UpdatePolicy.isSafeApkName("x/Humming-1.6.0.apk"))
    }

    @Test
    fun comparesVersionCodes() {
        assertTrue(UpdatePolicy.isNewer(1601, 1600))
        assertFalse(UpdatePolicy.isNewer(1600, 1600))
        assertFalse(UpdatePolicy.isNewer(1500, 1600))
    }

    @Test
    fun readsSha256SumsInBothFormats() {
        val a = "a".repeat(64)
        val b = "B".repeat(64)
        val sums = "$a  Humming-1.6.1.aab\n$b *Humming-1.6.1.apk\n${"c".repeat(64)}  Humming_1.6.1_universal.dmg\n"
        assertEquals("b".repeat(64), UpdatePolicy.sumFor(sums, "Humming-1.6.1.apk"))
        assertEquals(a, UpdatePolicy.sumFor(sums, "Humming-1.6.1.aab"))
        assertNull(UpdatePolicy.sumFor(sums, "Humming-1.6.2.apk"))
        assertNull(UpdatePolicy.sumFor("garbage", "Humming-1.6.1.apk"))
    }

    @Test
    fun certificateMatchIgnoresColonsAndCase() {
        assertTrue(UpdatePolicy.certMatches("8C:8B:68:BB:E6:3A:A4:2A:85:61:59:AA:A5:B0:C9:0C:D9:54:EE:F7:8A:98:0D:39:BC:B6:69:41:75:3E:1F:C6"))
        assertTrue(UpdatePolicy.certMatches("8c8b68bbe63aa42a856159aaa5b0c90cd954eef78a980d39bcb66941753e1fc6"))
        assertFalse(UpdatePolicy.certMatches("00".repeat(32)))
    }

    @Test
    fun backgroundChecksAreThrottled() {
        val six = 6L * 3600 * 1000
        assertTrue(UpdatePolicy.dueForCheck(nowMs = 10_000, lastMs = 0, everyMs = six))
        assertFalse(UpdatePolicy.dueForCheck(nowMs = six, lastMs = 1, everyMs = six))
        assertTrue(UpdatePolicy.dueForCheck(nowMs = six + 1, lastMs = 1, everyMs = six))
        assertTrue("clock moved back", UpdatePolicy.dueForCheck(nowMs = 5, lastMs = 1000, everyMs = six))
    }

    @Test
    fun autoDownloadWaitsForWifiUnlessAsked() {
        assertTrue(UpdatePolicy.mayDownload(autoDownload = true, unmetered = true, userAsked = false))
        assertFalse(UpdatePolicy.mayDownload(autoDownload = true, unmetered = false, userAsked = false))
        assertFalse(UpdatePolicy.mayDownload(autoDownload = false, unmetered = true, userAsked = false))
        assertTrue(UpdatePolicy.mayDownload(autoDownload = false, unmetered = false, userAsked = true))
    }
}
