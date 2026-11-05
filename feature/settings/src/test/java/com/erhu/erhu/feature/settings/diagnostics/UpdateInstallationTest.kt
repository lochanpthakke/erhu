package com.erhu.erhu.feature.settings.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInstallationTest {
    @Test fun `permission denial and a missing cached APK cannot launch installation`() {
        assertEquals(InstallPreparation.PERMISSION_REQUIRED, prepareUpdateInstall(true, false))
        assertEquals(InstallPreparation.MISSING_FILE, prepareUpdateInstall(false, true))
        assertEquals(InstallPreparation.READY, prepareUpdateInstall(true, true))
    }
    @Test fun `user cancellation offers another explicit install attempt`() {
        assertTrue(installationResultMessage(0).contains("cancelled"))
        assertTrue(installationResultMessage(0).contains("retry"))
        assertTrue(installationResultMessage(-1).contains("successfully"))
        assertTrue(installationResultMessage(1).contains("permission"))
    }
}
