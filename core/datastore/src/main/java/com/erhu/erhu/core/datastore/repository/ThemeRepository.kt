package com.erhu.erhu.core.datastore.repository

import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.designsystem.importer.ImportedThemeResult
import com.erhu.erhu.core.designsystem.skin.SkinManifest
import com.erhu.erhu.core.designsystem.theme.BuiltInThemes
import com.erhu.erhu.core.designsystem.theme.ThemeManifest
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
    val defaultStartSection: StateFlow<String>

    /** Metadata & Artwork provider choice as stored ("" until the user picks one). */
    val metadataProvider: StateFlow<String>

    /** Metadata & Artwork fields to fix as stored ("" until the user picks; older versions stored one action name). */
    val metadataFixAction: StateFlow<String>

    /** Auto-accept threshold for metadata matches as stored (0 until the user changes it). */
    val metadataConfidenceThreshold: StateFlow<Float>

    /** Fanart.tv personal API key as stored ("" until the user enters one). */
    val fanartTvApiKey: StateFlow<String>

    val sidebarGlassyness: StateFlow<Float>
    val uiGlassyness: StateFlow<Float>
    val popupGlassOpacity: StateFlow<Float>

    suspend fun setAppTheme(themeId: String): AppResult<Unit>

    suspend fun setWidgetTheme(themeId: String): AppResult<Unit>

    suspend fun setLibraryLayoutMode(mode: String): AppResult<Unit>

    suspend fun setGlassEffectEnabled(enabled: Boolean): AppResult<Unit>

    suspend fun setSidebarGlassyness(value: Float): AppResult<Unit>

    suspend fun setUiGlassyness(value: Float): AppResult<Unit>

    suspend fun setPopupGlassOpacity(value: Float): AppResult<Unit>

    suspend fun setDefaultStartSection(section: String): AppResult<Unit>

    suspend fun setMetadataProvider(provider: String): AppResult<Unit>

    suspend fun setMetadataFixAction(action: String): AppResult<Unit>

    suspend fun setMetadataConfidenceThreshold(threshold: Float): AppResult<Unit>

    suspend fun setFanartTvApiKey(apiKey: String): AppResult<Unit>

    suspend fun setCustomScanLocation(
        uri: String,
        path: String,
    ): AppResult<Unit>

    suspend fun setScanEntireDevice(): AppResult<Unit>

    suspend fun importPackage(inputStream: InputStream): AppResult<ImportedThemeResult>

    suspend fun acknowledgeRollback(): AppResult<Unit>

    suspend fun recordVerifiedStableTheme(
        themeId: String,
        checksum: String,
    ): AppResult<Unit>

    suspend fun triggerRollback(reason: String): AppResult<Unit>
}
