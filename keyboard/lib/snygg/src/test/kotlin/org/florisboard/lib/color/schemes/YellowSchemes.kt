/*
 * Copyright (C) 2026 Humming Keyboard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.florisboard.lib.color.schemes

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Test fixtures for SnyggThemeTest: the upstream test imports these two schemes, but the upstream tree has no
// org.florisboard.lib.color.schemes package, so `./gradlew testDebugUnitTest` failed to compile lib:snygg's tests.
// Any two distinct schemes work; the test only compares the theme's resolved colours with these values.

val yellowLightScheme = lightColorScheme(
    primary = Color(0xFF6D5E0F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF8E287),
    onPrimaryContainer = Color(0xFF221B00),
    secondary = Color(0xFF665E40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEE2BC),
    onSecondaryContainer = Color(0xFF211B04),
    tertiary = Color(0xFF43664E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF9EE),
    onBackground = Color(0xFF1E1B13),
    surface = Color(0xFFFFF9EE),
    onSurface = Color(0xFF1E1B13),
    surfaceVariant = Color(0xFFEAE2D0),
    onSurfaceVariant = Color(0xFF4B4739),
    outline = Color(0xFF7C7767),
)

val yellowDarkScheme = darkColorScheme(
    primary = Color(0xFFDBC66E),
    onPrimary = Color(0xFF3A3000),
    primaryContainer = Color(0xFF534600),
    onPrimaryContainer = Color(0xFFF8E287),
    secondary = Color(0xFFD1C6A1),
    onSecondary = Color(0xFF363016),
    secondaryContainer = Color(0xFF4E472A),
    onSecondaryContainer = Color(0xFFEEE2BC),
    tertiary = Color(0xFFA9D0B3),
    onTertiary = Color(0xFF143723),
    background = Color(0xFF15130B),
    onBackground = Color(0xFFE8E2D4),
    surface = Color(0xFF15130B),
    onSurface = Color(0xFFE8E2D4),
    surfaceVariant = Color(0xFF4B4739),
    onSurfaceVariant = Color(0xFFCDC6B4),
    outline = Color(0xFF969080),
)
