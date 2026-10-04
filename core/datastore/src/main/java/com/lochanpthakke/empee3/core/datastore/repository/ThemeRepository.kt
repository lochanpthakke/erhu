package com.lochanpthakke.empee3.core.datastore.repository

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.designsystem.importer.ImportedThemeResult
import com.lochanpthakke.empee3.core.designsystem.skin.SkinManifest
import com.lochanpthakke.empee3.core.designsystem.theme.BuiltInThemes
import com.lochanpthakke.empee3.core.designsystem.theme.ThemeManifest
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream

interface ThemeRepository {
    /**
     * The resolved app theme. For System Default this is Default Dark (no device
     * appearance is known here); the UI resolves it with [appThemeId].
     */
    val appThemeState: StateFlow<ThemeManifest>

    /** The selected app theme id, e.g. [BuiltInThemes.SYSTEM_DEFAULT_ID]. */
    val appThemeId: StateFlow<String>
    val appSkinState: StateFlow<SkinManifest?>
    val widgetThemeState: StateFlow<ThemeManifest>
    val widgetSkinState: StateFlow<SkinManifest?>
    val libraryLayoutMode: StateFlow<String>
    val isRollbackFlagActive: StateFlow<Boolean>

    /** Glass material on floating surfaces, independent of the theme. */
    val glassEffectEnabled: StateFlow<Boolean>

    val customScanLocationUri: StateFlow<String>
    val customScanLocationPath: StateFlow<String>
    val isCustomScanLocationEnabled: StateFlow<Boolean>
    val scanLocationConfigured: StateFlow<Boolean>

    suspend fun setAppTheme(themeId: String): AppResult<Unit>

    suspend fun setWidgetTheme(themeId: String): AppResult<Unit>

    suspend fun setLibraryLayoutMode(mode: String): AppResult<Unit>

    suspend fun setGlassEffectEnabled(enabled: Boolean): AppResult<Unit>

    suspend fun setCustomScanLocation(uri: String, path: String): AppResult<Unit>

    suspend fun setScanEntireDevice(): AppResult<Unit>

    suspend fun importPackage(inputStream: InputStream): AppResult<ImportedThemeResult>

    suspend fun acknowledgeRollback(): AppResult<Unit>

    suspend fun recordVerifiedStableTheme(
        themeId: String,
        checksum: String,
    ): AppResult<Unit>

    suspend fun triggerRollback(reason: String): AppResult<Unit>
}
