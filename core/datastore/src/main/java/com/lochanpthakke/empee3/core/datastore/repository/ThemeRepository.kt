package com.lochanpthakke.empee3.core.datastore.repository

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.designsystem.importer.ImportedThemeResult
import com.lochanpthakke.empee3.core.designsystem.skin.SkinManifest
import com.lochanpthakke.empee3.core.designsystem.theme.ThemeManifest
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream

interface ThemeRepository {
    val appThemeState: StateFlow<ThemeManifest>
    val appSkinState: StateFlow<SkinManifest?>
    val widgetThemeState: StateFlow<ThemeManifest>
    val widgetSkinState: StateFlow<SkinManifest?>
    val libraryLayoutMode: StateFlow<String>
    val isRollbackFlagActive: StateFlow<Boolean>

    suspend fun setAppTheme(themeId: String): AppResult<Unit>
    suspend fun setWidgetTheme(themeId: String): AppResult<Unit>
    suspend fun setLibraryLayoutMode(mode: String): AppResult<Unit>
    suspend fun importPackage(inputStream: InputStream): AppResult<ImportedThemeResult>
    suspend fun acknowledgeRollback(): AppResult<Unit>
    suspend fun recordVerifiedStableTheme(themeId: String, checksum: String): AppResult<Unit>
    suspend fun triggerRollback(reason: String): AppResult<Unit>
}
