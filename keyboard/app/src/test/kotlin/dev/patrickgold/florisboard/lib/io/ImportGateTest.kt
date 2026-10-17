package dev.patrickgold.florisboard.lib.io

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportGateTest {
    private val pkg = "com.humming.keyboard"
    private val ext = "application/vnd.florisboard.extension+zip"

    @Test
    fun acceptsExtensionFromAnotherProvider() {
        assertTrue(ImportGate.isAllowed("content", "com.android.providers.downloads.documents", ext, pkg))
        assertTrue(ImportGate.isAllowed("CONTENT", "org.mozilla.firefox.fileprovider", "application/octet-stream", pkg))
        assertTrue(ImportGate.isAllowed("content", "media", "application/zip; charset=binary", pkg))
    }

    @Test
    fun rejectsNonContentSchemes() {
        assertFalse(ImportGate.isAllowed("file", "", ext, pkg))
        assertFalse(ImportGate.isAllowed("https", "example.org", ext, pkg))
        assertFalse(ImportGate.isAllowed(null, "x", ext, pkg))
    }

    @Test
    fun rejectsOwnProviders() {
        assertFalse(ImportGate.isAllowed("content", pkg, ext, pkg))
        assertFalse(ImportGate.isAllowed("content", "$pkg.provider", ext, pkg))
        assertFalse(ImportGate.isAllowed("content", "0@$pkg.provider", ext, pkg))
    }

    @Test
    fun rejectsUnexpectedTypes() {
        assertFalse(ImportGate.isAllowed("content", "other.app", "text/xml", pkg))
        assertFalse(ImportGate.isAllowed("content", "other.app", null, pkg))
        assertFalse(ImportGate.isAllowed("content", null, ext, pkg))
    }
}
