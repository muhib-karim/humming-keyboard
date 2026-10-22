/*
 * Copyright 2026 Humming contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.humming.keyboard

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Humming 1.7.1: load time of the Fast voice model on the CI emulator (android-emulator.yml). Runs only when asked for
 * with `-e humming.modelBench true`, because it downloads the 104 MB model. The download, SHA-256 check and unpacking
 * go through the app's own [ModelStore.download]; the load is the app's [SherpaEngine], timed by the engine itself.
 * The first load reads the files from storage; the two reloads show the cost with the files already in memory.
 * Result: files/ci/model_load.json in the app's private storage (the workflow reads it with run-as).
 */
@RunWith(AndroidJUnit4::class)
class ModelLoadBenchTest {
    @Test
    fun fastModelLoadTime() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("run with -e humming.modelBench true", args.getString("humming.modelBench") == "true")
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val m = VoiceModels.FAST

        val t0 = SystemClock.elapsedRealtime()
        if (!ModelStore.ready(ctx, m)) ModelStore.download(ctx, m) { _, _ -> }
        val downloadMs = SystemClock.elapsedRealtime() - t0
        assertTrue("model downloaded and verified", ModelStore.ready(ctx, m))

        val threads = CoreLoader.threads()
        val loads = JSONArray()
        var warmupMs = -1L
        repeat(LOADS) { i ->
            SherpaEngine(m, ModelStore.dir(ctx, m), threads).use { e ->
                loads.put(e.loadMs)
                if (i == 0) {
                    val w0 = SystemClock.elapsedRealtime()
                    e.warmup()
                    warmupMs = SystemClock.elapsedRealtime() - w0
                }
            }
        }
        val out = JSONObject()
            .put("model", m.id)
            .put("download_ms", downloadMs)
            .put("load_ms", loads)
            .put("warmup_decode_ms", warmupMs)
            .put("threads", threads)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull())
            .put("sdk", Build.VERSION.SDK_INT)
        File(ctx.filesDir, "ci").apply { mkdirs() }.resolve("model_load.json").writeText(out.toString(2))
        android.util.Log.i("HUMMING", "HUMMING_MODEL_BENCH $out")
    }

    private companion object {
        const val LOADS = 3
    }
}
