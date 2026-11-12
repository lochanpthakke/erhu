package com.erhu.erhu.feature.settings.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test
import com.erhu.erhu.core.common.R as CommonR

class UpdateInstallationTest {
    @Test fun `permission denial and a missing cached APK cannot launch installation`() {
        assertEquals(InstallPreparation.PERMISSION_REQUIRED, prepareUpdateInstall(true, false))
        assertEquals(InstallPreparation.MISSING_FILE, prepareUpdateInstall(false, true))
        assertEquals(InstallPreparation.READY, prepareUpdateInstall(true, true))
    }

    @Test fun `user cancellation offers another explicit install attempt`() {
        assertEquals(CommonR.string.update_install_cancelled, installationResultMessageRes(0))
        assertEquals(CommonR.string.update_install_success, installationResultMessageRes(-1))
        assertEquals(CommonR.string.update_install_failed, installationResultMessageRes(1))
    }
}
