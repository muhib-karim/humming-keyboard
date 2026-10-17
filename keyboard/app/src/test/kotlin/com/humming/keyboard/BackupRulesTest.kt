package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Personal data never goes to a cloud backup: only settings and the ime files are included. */
class BackupRulesTest {
    private fun includes(file: String, section: String? = null): List<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$file"))
        val root = if (section == null) doc.documentElement else doc.getElementsByTagName(section).item(0) as org.w3c.dom.Element
        val nodes = root.getElementsByTagName("include")
        return (0 until nodes.length).map { i ->
            val e = nodes.item(i) as org.w3c.dom.Element
            e.getAttribute("domain") + ":" + e.getAttribute("path")
        }
    }

    private val cloudAllowed = listOf("root:jetpref_datastore", "file:ime")

    @Test
    fun cloudBackupCarriesOnlySettingsAndExtensions() {
        assertEquals(cloudAllowed, includes("data_extraction_rules.xml", "cloud-backup"))
        assertEquals(cloudAllowed, includes("backup_rules.xml"))
    }

    @Test
    fun deviceTransferMayCarryLearnedWords() {
        val d = includes("data_extraction_rules.xml", "device-transfer")
        assertTrue(d.containsAll(cloudAllowed))
        assertFalse(d.any { it.contains("history") })
    }

    @Test
    fun theManifestUsesSeparateRuleFiles() {
        val m = File("src/main/AndroidManifest.xml").readText()
        assertTrue(m.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(m.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertFalse(File("src/main/res/xml-v31/backup_rules.xml").exists())
    }
}
