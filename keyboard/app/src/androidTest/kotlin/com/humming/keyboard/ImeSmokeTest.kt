/*
 * Copyright 2026 Humming contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.humming.keyboard

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.patrickgold.florisboard.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.regex.Pattern

/**
 * Humming 1.7.1: the emulator smoke test (android-emulator.yml). Makes Humming the keyboard, opens Android's keyboard
 * settings and Humming's own settings, then types "hello" by tapping Humming's keys in the "Try out your setup" field.
 * Screenshots go to [SHOTS] on the device (a shell-owned directory; the workflow pulls them as an artifact). On a
 * failure the screen and the window hierarchy are saved too.
 */
@RunWith(AndroidJUnit4::class)
class ImeSmokeTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(inst)
    private val ctx = inst.targetContext
    private val ime = "${ctx.packageName}/dev.patrickgold.florisboard.FlorisImeService"

    @get:Rule
    val evidence = object : TestWatcher() {
        override fun failed(e: Throwable?, description: Description?) {
            shot("failure")
            val dump = ByteArrayOutputStream()
            runCatching { device.dumpWindowHierarchy(dump) }
            File(ctx.filesDir, "ci").apply { mkdirs() }.resolve("failure_hierarchy.xml").writeBytes(dump.toByteArray())
        }
    }

    @Before
    fun makeHummingTheKeyboard() {
        sh("mkdir -p $SHOTS")
        sh("ime enable $ime")
        sh("ime set $ime")
        sh("pm grant ${ctx.packageName} android.permission.POST_NOTIFICATIONS")
        device.wakeUp()
        sh("wm dismiss-keyguard")
    }

    @Test
    fun opensKeyboardSettingsAndTypesWithHumming() {
        // 1. Android's keyboard settings list Humming.
        ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(NEW_TASK))
        val listed = device.wait(Until.findObject(By.text("Humming")), TIMEOUT)
        assertNotNull("Humming in Android's keyboard settings", listed)
        shot("01_android_keyboard_settings")

        // 2. Humming's settings: finish the first-run setup if it shows, until the home screen's preview field is there.
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(NEW_TASK))
        val field = homePreviewField()
        assertNotNull("Humming's settings home with the \"Try out your setup\" field", field)
        shot("03_settings_home")

        // 3. Focus the field: Humming's keyboard opens.
        field!!.click()
        assertNotNull("Humming's keyboard is shown", device.wait(Until.findObject(By.desc(key("q"))), TIMEOUT))
        device.waitForIdle()
        shot("04_keyboard")

        // 4. Type with Humming's keys (each key carries its label as its accessibility name since 1.7.0).
        for (c in "hello") {
            val k = device.wait(Until.findObject(By.desc(key(c.toString()))), TIMEOUT)
            assertNotNull("key $c", k)
            k!!.click()
            device.waitForIdle()
        }
        val typed = device.wait(
            Until.hasObject(By.clazz("android.widget.EditText").text(Pattern.compile("(?i)\\s*hello\\s*"))),
            TIMEOUT,
        )
        shot("05_typed")
        assertTrue("the field shows the typed word", typed)
    }

    private fun homePreviewField(): UiObject2? {
        val hint = ctx.getString(R.string.settings__preview_keyboard)
        val buttons = listOf(
            ctx.getString(R.string.setup__grant_notification_permission__btn),
            ctx.getString(R.string.setup__finish_up__finish_btn),
        )
        val deadline = SystemClock.uptimeMillis() + SETUP_TIMEOUT
        while (SystemClock.uptimeMillis() < deadline) {
            device.wait(Until.findObject(By.text(hint)), STEP_TIMEOUT)?.let { return it }
            device.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button"))?.click()
            for (label in buttons) {
                device.findObject(By.text(label))?.let {
                    shot("02_setup")
                    it.click()
                }
            }
        }
        return null
    }

    private fun key(label: String): Pattern = Pattern.compile("(?i)" + Pattern.quote(label))

    private fun shot(name: String) = sh("screencap -p $SHOTS/$name.png")

    private fun sh(cmd: String): String = device.executeShellCommand(cmd)

    private companion object {
        const val SHOTS = "/data/local/tmp/humming"
        const val TIMEOUT = 15_000L
        const val STEP_TIMEOUT = 2_000L
        const val SETUP_TIMEOUT = 45_000L
        const val NEW_TASK = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
}
