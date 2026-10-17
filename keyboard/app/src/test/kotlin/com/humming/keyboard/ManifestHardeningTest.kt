package com.humming.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestHardeningTest {
    @Test
    fun releaseIsNotShellProfileable() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("<profileable android:shell=\"true\""))
        assertTrue(manifest.contains("android:shell=\"\${'$'}{profileableShell}\""))
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("manifestPlaceholders[\"profileableShell\"] = \"false\""))
    }

    @Test
    fun themeAssetsStayInsideTheirDirectory() {
        val t = File("src/main/kotlin/dev/patrickgold/florisboard/ime/theme/FlorisAssetResolver.kt").readText()
        assertTrue(t.contains("canonicalPath.startsWith(basePath + java.io.File.separator)"))
    }
}
