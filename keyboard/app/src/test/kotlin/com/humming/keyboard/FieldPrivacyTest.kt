/*
 * Copyright 2026 Humming contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.humming.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldPrivacyTest {
    @Test
    fun passwordVariationsForceIncognito() {
        assertTrue(FieldPrivacy.isPassword(0x01 or 0x80)) // text password
        assertTrue(FieldPrivacy.isPassword(0x01 or 0x90)) // visible password
        assertTrue(FieldPrivacy.isPassword(0x01 or 0xe0)) // web password
        assertTrue(FieldPrivacy.isPassword(0x02 or 0x10)) // number password
        assertTrue(FieldPrivacy.forcesIncognito(0x01 or 0x80, 0))
    }

    @Test
    fun ordinaryFieldsDoNot() {
        assertFalse(FieldPrivacy.isPassword(0x01)) // plain text
        assertFalse(FieldPrivacy.isPassword(0x01 or 0x20)) // email address
        assertFalse(FieldPrivacy.isPassword(0x01 or 0x00004000)) // text + cap sentences flag
        assertFalse(FieldPrivacy.isPassword(0x02)) // number
        assertFalse(FieldPrivacy.isPassword(0x03 or 0x10)) // phone class, not a number password
        assertFalse(FieldPrivacy.forcesIncognito(0x01, 0x06))
    }

    @Test
    fun noPersonalisedLearningForcesIncognito() {
        assertTrue(FieldPrivacy.forcesIncognito(0x01, 0x01000000 or 0x06))
    }
}
