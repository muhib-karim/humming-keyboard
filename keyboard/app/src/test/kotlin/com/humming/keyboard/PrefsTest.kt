package com.humming.keyboard

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Humming 1.7.1: Settings > Voice as the keyboard reads it ([Prefs]), on real SharedPreferences (Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PrefsTest {
    private val ctx: Context = RuntimeEnvironment.getApplication()
    private val history get() = File(ctx.filesDir, "history.json")

    @Before fun freshInstall() {
        Prefs.get(ctx).edit().clear().commit()
        history.delete()
        Prefs.currentPackage = null
    }

    @Test fun defaultsOfAFreshInstall() {
        assertTrue("clean mode", Prefs.cleanMode(ctx))
        assertTrue("remove fillers", Prefs.removeFillers(ctx))
        assertFalse("accuracy mode", Prefs.accuracyMode(ctx))
        assertTrue("models on Wi-Fi only", Prefs.wifiOnly(ctx))
        assertEquals(VoiceModels.FAST.id, Prefs.voiceModel(ctx))
        assertTrue("Bluetooth mic", Prefs.bluetoothMic(ctx))
        assertFalse("voice incognito", Prefs.voiceIncognito(ctx))
        assertEquals(Prefs.ENGINE_OFFLINE, Prefs.engine(ctx))
        assertTrue("live typing", Prefs.liveTyping(ctx))
        assertEquals("", Prefs.dictionaryText(ctx))
        assertEquals(emptyList<String>(), Prefs.dictionary(ctx))
        assertEquals(Prefs.MODE_DEVICE, Prefs.mode(ctx))
    }

    @Test fun switchesAreStoredAndReadBack() {
        Prefs.set(ctx, Prefs.KEY_CLEAN, false)
        Prefs.set(ctx, Prefs.KEY_ACCURACY, true)
        Prefs.set(ctx, Prefs.KEY_FILLERS, false)
        Prefs.set(ctx, Prefs.KEY_WIFI_ONLY, false)
        Prefs.set(ctx, Prefs.KEY_VOICE_MODEL, VoiceModels.BEST.id)
        Prefs.set(ctx, Prefs.KEY_LIVE_TYPING, false)
        assertFalse(Prefs.cleanMode(ctx))
        assertTrue(Prefs.accuracyMode(ctx))
        assertFalse(Prefs.removeFillers(ctx))
        assertFalse(Prefs.wifiOnly(ctx))
        assertEquals(VoiceModels.BEST.id, Prefs.voiceModel(ctx))
        assertFalse(Prefs.liveTyping(ctx))
    }

    @Test fun onlyTheExactOnlineValueTurnsOnTheOnlineEngine() {
        for (v in listOf("ONLINE", "cloud", "", "offline")) {
            Prefs.set(ctx, Prefs.KEY_ENGINE, v)
            assertEquals(v, Prefs.ENGINE_OFFLINE, Prefs.engine(ctx))
        }
        Prefs.set(ctx, Prefs.KEY_ENGINE, Prefs.ENGINE_ONLINE)
        assertEquals(Prefs.ENGINE_ONLINE, Prefs.engine(ctx))
    }

    @Test fun anUnknownSavedModeIsTheOnDeviceEngine() {
        Prefs.setMode(ctx, "whatever")
        assertEquals(Prefs.MODE_DEVICE, Prefs.mode(ctx))
    }

    @Test fun freshInstallRetentionIsTheDefaultAndIsStoredOnce() {
        assertEquals(HistoryRetention.DEFAULT, Prefs.retention(ctx))
        assertEquals(HistoryRetention.DEFAULT, Prefs.get(ctx).getString(Prefs.KEY_RETENTION, null))
        // Saved dictations arriving later do not change a choice that is already stored.
        history.writeText("[{\"text\":\"hi\"}]")
        assertEquals(HistoryRetention.DEFAULT, Prefs.retention(ctx))
    }

    @Test fun anUpdateWithSavedHistoryKeepsItForever() {
        history.writeText("[{\"text\":\"hi\"}]")
        assertEquals(HistoryRetention.FOREVER, Prefs.retention(ctx))
        assertEquals(HistoryRetention.FOREVER, Prefs.get(ctx).getString(Prefs.KEY_RETENTION, null))
    }

    @Test fun anEmptyHistoryFileCountsAsNoHistory() {
        history.writeText("[]")
        assertEquals(HistoryRetention.DEFAULT, Prefs.retention(ctx))
    }

    @Test fun theUsersRetentionChoiceWins() {
        history.writeText("[{\"text\":\"hi\"}]")
        Prefs.set(ctx, Prefs.KEY_RETENTION, HistoryRetention.WEEK)
        assertEquals(HistoryRetention.WEEK, Prefs.retention(ctx))
    }

    @Test fun customWordsFollowTheFocusedApp() {
        Prefs.set(ctx, Prefs.KEY_DICTIONARY, "Kubernetes @slack\nParakeet\ncube cuddle -> kubectl @termux")
        Prefs.currentPackage = "com.Slack"
        assertEquals(listOf("Kubernetes", "Parakeet"), Prefs.dictionary(ctx))
        assertEquals(emptyList<Pair<String, String>>(), Prefs.replacements(ctx))
        Prefs.currentPackage = "com.termux"
        assertEquals(listOf("Parakeet", "kubectl"), Prefs.dictionary(ctx))
        assertEquals(listOf("cube cuddle" to "kubectl"), Prefs.replacements(ctx))
        Prefs.currentPackage = null
        assertEquals(listOf("Parakeet"), Prefs.dictionary(ctx))
    }
}
