package com.reals.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupManifestTest {
    @Test
    fun androidBackupIsDisabledAndBackupRuleReferencesAreAbsent() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertFalse(manifest.contains("android:dataExtractionRules"))
        assertFalse(manifest.contains("android:fullBackupContent"))
        assertFalse(File("src/main/res/xml/backup_rules.xml").exists())
        assertFalse(File("src/main/res/xml/data_extraction_rules.xml").exists())
    }
}
