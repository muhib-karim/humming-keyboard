package com.humming.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Copy to clipboard" share target: another app must not be able to make Humming read its own private files. */
class SharedUriPolicyTest {
    private val own = "com.humming.keyboard"

    @Test fun otherAppsContentIsAccepted() {
        assertTrue(SharedUriPolicy.acceptable("content", "com.google.android.apps.photos.contentprovider", own))
        assertTrue(SharedUriPolicy.acceptable("content", "media", own))
    }

    @Test fun ownProvidersAreRejected() {
        assertFalse(SharedUriPolicy.acceptable("content", "com.humming.keyboard.provider.clipboard", own))
        assertFalse(SharedUriPolicy.acceptable("content", "com.humming.keyboard.provider.file", own))
        assertFalse(SharedUriPolicy.acceptable("content", "com.humming.keyboard", own))
        assertFalse(SharedUriPolicy.acceptable("CONTENT", "com.humming.keyboard.debug.provider.file", "com.humming.keyboard.debug"))
    }

    @Test fun nonContentSchemesAreRejected() {
        assertFalse(SharedUriPolicy.acceptable("file", null, own))
        assertFalse(SharedUriPolicy.acceptable(null, "media", own))
        assertFalse(SharedUriPolicy.acceptable("content", null, own))
    }

    @Test fun lookalikeAuthorityOfAnotherAppIsAccepted() {
        assertTrue(SharedUriPolicy.acceptable("content", "com.humming.keyboardfan.provider", own))
    }
}
