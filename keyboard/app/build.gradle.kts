/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.agp.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mikepenz.aboutlibraries)
}

val projectMinSdk: String by project
val projectTargetSdk: String by project
val projectCompileSdk: String by project
val projectVersionCode: String by project
val projectVersionName: String by project
val projectVersionNameSuffix = projectVersionName.substringAfter("-", "").let { suffix ->
    if (suffix.isNotEmpty()) {
        "-$suffix"
    } else {
        suffix
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.set(listOf(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-Xjvm-default=all-compatibility",
            "-Xwhen-guards",
        ))
    }
}

// Humming: on-device voice engine = sherpa-onnx (Apache-2.0) running NVIDIA Parakeet (CC-BY-4.0 weights, downloaded
// by the app on first use). The sherpa-onnx Android library is fetched once from its GitHub release and pinned by
// SHA-256, so a fresh clone builds with plain `./gradlew assembleRelease`.
val sherpaVersion = "1.13.8"
val sherpaSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
val sherpaAar: File = file("libs/sherpa-onnx-$sherpaVersion.aar").also { aar ->
    fun sha(f: File): String = MessageDigest.getInstance("SHA-256").let { md: MessageDigest ->
        f.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }
    if (!aar.isFile || sha(aar) != sherpaSha256) {
        aar.parentFile.mkdirs()
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
        logger.lifecycle("Humming: fetching $url")
        URI(url).toURL().openStream().use { input -> aar.outputStream().use { input.copyTo(it) } }
        check(sha(aar) == sherpaSha256) { "sherpa-onnx AAR checksum mismatch" }
    }
}

// Humming: release signing (prep). When HUMMING_KEYSTORE (path to the owner's upload/release key, PKCS12 or JKS) and
// HUMMING_KEYSTORE_PASSWORD are set, release builds are signed with that key; otherwise they stay signed with the
// debug key exactly as before. No key material lives in the repository (docs/RELEASE_SIGNING.md).
val hummingKeystore: File? = System.getenv("HUMMING_KEYSTORE")?.takeIf { it.isNotBlank() }?.let { file(it) }?.takeIf { it.isFile }
val hummingKeystorePassword: String? = System.getenv("HUMMING_KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() }
val hummingReleaseSigning: Boolean = hummingKeystore != null && hummingKeystorePassword != null

// Humming 1.6: in-app updates are for the sideloaded APK only. Google Play does not allow an app to update itself, so
// the Play bundle is built with -Phumming.play=true: SELF_UPDATE = false and REQUEST_INSTALL_PACKAGES is removed from
// its manifest (src/play/AndroidManifest.xml). The android-release workflow checks both.
val hummingPlay: Boolean = (project.findProperty("humming.play") as String?) == "true"

android {
    namespace = "dev.patrickgold.florisboard"
    compileSdk = projectCompileSdk.toInt()
    buildToolsVersion = tools.versions.buildTools.get()
    ndkVersion = tools.versions.ndk.get()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }

    defaultConfig {
        applicationId = "com.humming.keyboard" // Humming (upstream: dev.patrickgold.florisboard)
        // <profileable android:shell> is only true for the benchmark build (and debug); release ships false.
        manifestPlaceholders["profileableShell"] = "false"
        minSdk = projectMinSdk.toInt()
        targetSdk = projectTargetSdk.toInt()
        versionCode = projectVersionCode.toInt()
        versionName = projectVersionName.substringBefore("-")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BUILD_COMMIT_HASH", "\"${getGitCommitHash().get()}\"")
        buildConfigField("String", "FLADDONS_API_VERSION", "\"v~draft2\"")
        buildConfigField("boolean", "SELF_UPDATE", if (hummingPlay) "false" else "true")
        buildConfigField("String", "FLADDONS_STORE_URL", "\"beta.addons.florisboard.org\"")
        // Humming: the on-device engine ships for 64-bit ARM phones and x86_64 emulators only. A build that asks for a
        // release task makes the phone APK (arm64-v8a only; the x86_64 libraries add 13 MB and no phone uses them);
        // debug builds keep both for the CI emulator. -Phumming.abis=... overrides either (e.g. arm64-v8a,x86_64).
        val hummingAbis = (project.findProperty("humming.abis") as String?)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        val releaseRequested = project.gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
        ndk { abiFilters += hummingAbis ?: if (releaseRequested) listOf("arm64-v8a") else listOf("arm64-v8a", "x86_64") }

        sourceSets {
            maybeCreate("main").apply {
                assets {
                    srcDirs("src/main/assets")
                }
                java {
                    srcDirs("src/main/kotlin")
                }
            }
        }
    }

    bundle {
        language {
            // We disable language split because FlorisBoard does not use
            // runtime Google Play Service APIs and thus cannot dynamically
            // request to download the language resources for a specific locale.
            enableSplit = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (hummingReleaseSigning) {
            create("release") {
                storeFile = hummingKeystore
                storePassword = hummingKeystorePassword
                keyAlias = System.getenv("HUMMING_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "humming"
                keyPassword = System.getenv("HUMMING_KEY_PASSWORD")?.takeIf { it.isNotEmpty() } ?: hummingKeystorePassword
            }
        }
    }

    buildTypes {
        named("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug+${getGitCommitHash(short = true).get()}"

            isDebuggable = true
            isJniDebuggable = false
            // Humming 1.7.1: JaCoCo line coverage of the unit tests (createDebugUnitTestCoverageReport, keyboard.yml).
            enableUnitTestCoverage = true
            manifestPlaceholders["profileableShell"] = "true"

            // Humming: app icon and name
            resValue("mipmap", "floris_app_icon", "@mipmap/ic_humming")
            resValue("mipmap", "floris_app_icon_round", "@mipmap/ic_humming")
            resValue("drawable", "floris_app_icon_foreground", "@drawable/ic_humming_foreground")
            resValue("string", "floris_app_name", "Humming")
        }

        named("release") {
            versionNameSuffix = projectVersionNameSuffix
            // Humming: signed with the owner's key when HUMMING_KEYSTORE is set (store builds); otherwise with the
            // development key so the sideload APK still installs anywhere (docs/RELEASE_SIGNING.md).
            signingConfig = signingConfigs.getByName(if (hummingReleaseSigning) "release" else "debug")

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = true
            isShrinkResources = true

            resValue("mipmap", "floris_app_icon", "@mipmap/ic_humming")
            resValue("mipmap", "floris_app_icon_round", "@mipmap/ic_humming")
            resValue("drawable", "floris_app_icon_foreground", "@drawable/ic_humming_foreground")
            resValue("string", "floris_app_name", "Humming")
        }

        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            manifestPlaceholders["profileableShell"] = "true" // macrobenchmark / baseline profiles need it
        }
    }

    if (hummingPlay) {
        sourceSets.getByName("release").manifest.srcFile("src/play/AndroidManifest.xml")
    }

    aboutLibraries {
        collect {
            configPath = file("src/main/config")
        }
    }

    packaging {
        jniLibs {
            // Humming: extract native libs at install time (onnxruntime loads from the app's lib dir).
            useLegacyPackaging = true
        }
    }

    lint {
        // Humming 1.7.1: lint is a CI gate (keyboard.yml, job "lint"). lint.xml in this directory is the rule
        // configuration; lint-baseline.xml holds only the issues that existed before 1.7.1 (docs/ci/STATIC_ANALYSIS.md).
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkDependencies = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

// Humming 1.7.1: the instrumented-test APK inherits the build type's resValues, which point at the app's own icons
// (@mipmap/ic_humming is not in the test APK) and break its resource linking. The test APK needs none of them.
androidComponents {
    onVariants { variant ->
        variant.androidTest?.resValues?.empty()
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Robolectric loads classes through its own class loader; JaCoCo only counts them with this flag.
    extensions.findByType(JacocoTaskExtension::class.java)?.apply {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    // testImplementation(composeBom)
    // androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.collection.ktx)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.cache4k)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mikepenz.aboutlibraries.core)
    implementation(libs.mikepenz.aboutlibraries.compose)
    implementation(libs.patrickgold.compose.tooltip)
    implementation(libs.patrickgold.jetpref.datastore.model)
    ksp(libs.patrickgold.jetpref.datastore.model.processor)
    implementation(libs.patrickgold.jetpref.datastore.ui)
    implementation(libs.patrickgold.jetpref.material.ui)

    implementation(projects.lib.android)
    implementation(projects.lib.color)
    implementation(projects.lib.compose)
    implementation(projects.lib.kotlin)
    implementation(projects.lib.snygg)

    testImplementation(libs.kotlin.test.junit5)
    // Humming: speech engine (sherpa-onnx + onnxruntime, Apache-2.0 / MIT) and the model archive reader (Apache-2.0).
    implementation(files(sherpaAar))
    implementation("org.apache.commons:commons-compress:1.27.1")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.13.4")
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
    // Humming 1.7.1: the emulator smoke test drives the real keyboard with UiAutomator (ImeSmokeTest).
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

fun getGitCommitHash(short: Boolean = false): Provider<String> {
    if (!File(".git").exists()) {
        return providers.provider { "null" }
    }

    val execProvider = providers.exec {
        if (short) {
            commandLine("git", "rev-parse", "--short", "HEAD")
        } else {
            commandLine("git", "rev-parse", "HEAD")
        }
    }
    return execProvider.standardOutput.asText.map { it.trim() }
}
