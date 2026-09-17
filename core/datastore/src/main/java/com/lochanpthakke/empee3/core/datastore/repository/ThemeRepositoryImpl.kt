package com.lochanpthakke.empee3.core.datastore.repository

import androidx.datastore.core.DataStore
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.designsystem.importer.ImportedThemeResult
import com.lochanpthakke.empee3.core.designsystem.importer.ThemeImporter
import com.lochanpthakke.empee3.core.designsystem.skin.SkinManifest
import com.lochanpthakke.empee3.core.designsystem.theme.BuiltInThemes
import com.lochanpthakke.empee3.core.designsystem.theme.ThemeManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooGenericExceptionCaught", "LongParameterList")
class ThemeRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<PlaybackStateProto>,
        private val themeImporter: ThemeImporter,
    ) : ThemeRepository {
        private val scope = CoroutineScope(Dispatchers.IO)

        private val _appThemeState = MutableStateFlow<ThemeManifest>(BuiltInThemes.DEFAULT_DARK)
        override val appThemeState: StateFlow<ThemeManifest> = _appThemeState.asStateFlow()

        private val _appSkinState = MutableStateFlow<SkinManifest?>(null)
        override val appSkinState: StateFlow<SkinManifest?> = _appSkinState.asStateFlow()

        private val _widgetThemeState = MutableStateFlow<ThemeManifest>(BuiltInThemes.DEFAULT_DARK)
        override val widgetThemeState: StateFlow<ThemeManifest> = _widgetThemeState.asStateFlow()

        private val _widgetSkinState = MutableStateFlow<SkinManifest?>(null)
        override val widgetSkinState: StateFlow<SkinManifest?> = _widgetSkinState.asStateFlow()

        private val _libraryLayoutMode = MutableStateFlow("GRID")
        override val libraryLayoutMode: StateFlow<String> = _libraryLayoutMode.asStateFlow()

        private val _isRollbackFlagActive = MutableStateFlow(false)
        override val isRollbackFlagActive: StateFlow<Boolean> = _isRollbackFlagActive.asStateFlow()

        init {
            scope.launch {
                val proto = dataStore.data.first()
                _libraryLayoutMode.value = proto.libraryLayoutMode.ifBlank { "GRID" }
                _isRollbackFlagActive.value = proto.themeRollbackFlag
                resolveTheme(proto.appThemeId.ifBlank { BuiltInThemes.DEFAULT_DARK.id }, isApp = true)
                resolveTheme(proto.widgetThemeId.ifBlank { BuiltInThemes.DEFAULT_DARK.id }, isApp = false)
            }
        }

        private fun resolveTheme(
            id: String,
            isApp: Boolean,
        ) {
            val builtInTheme = BuiltInThemes.ALL_BUILT_IN_THEMES.find { it.id == id }
            if (builtInTheme != null) {
                if (isApp) {
                    _appThemeState.value = builtInTheme
                    _appSkinState.value = null
                } else {
                    _widgetThemeState.value = builtInTheme
                    _widgetSkinState.value = null
                }
                return
            }

            val builtInSkin = BuiltInThemes.ALL_BUILT_IN_SKINS.find { it.id == id }
            if (builtInSkin != null) {
                if (isApp) {
                    _appThemeState.value = BuiltInThemes.DEFAULT_DARK
                    _appSkinState.value = builtInSkin
                } else {
                    _widgetThemeState.value = BuiltInThemes.DEFAULT_DARK
                    _widgetSkinState.value = builtInSkin
                }
                return
            }

            if (isApp) {
                _appThemeState.value = BuiltInThemes.DEFAULT_DARK
                _appSkinState.value = null
            } else {
                _widgetThemeState.value = BuiltInThemes.DEFAULT_DARK
                _widgetSkinState.value = null
            }
        }

        override suspend fun setAppTheme(themeId: String): AppResult<Unit> =
            appResultOf {
                dataStore.updateData { it.toBuilder().setAppThemeId(themeId).build() }
                resolveTheme(themeId, isApp = true)
            }

        override suspend fun setWidgetTheme(themeId: String): AppResult<Unit> =
            appResultOf {
                dataStore.updateData { it.toBuilder().setWidgetThemeId(themeId).build() }
                resolveTheme(themeId, isApp = false)
            }

        override suspend fun setLibraryLayoutMode(mode: String): AppResult<Unit> =
            appResultOf {
                dataStore.updateData { it.toBuilder().setLibraryLayoutMode(mode).build() }
                _libraryLayoutMode.value = mode
            }

        override suspend fun importPackage(inputStream: InputStream): AppResult<ImportedThemeResult> =
            themeImporter.importPackage(inputStream)

        override suspend fun acknowledgeRollback(): AppResult<Unit> =
            appResultOf {
                dataStore.updateData { it.toBuilder().setThemeRollbackFlag(false).build() }
                _isRollbackFlagActive.value = false
            }

        override suspend fun recordVerifiedStableTheme(
            themeId: String,
            checksum: String,
        ): AppResult<Unit> =
            appResultOf {
                dataStore.updateData {
                    it
                        .toBuilder()
                        .setLastVerifiedStableThemeId(themeId)
                        .setLastVerifiedStableChecksum(checksum)
                        .build()
                }
            }

        override suspend fun triggerRollback(reason: String): AppResult<Unit> =
            appResultOf {
                val lastStable =
                    dataStore.data
                        .first()
                        .lastVerifiedStableThemeId
                        .ifBlank { BuiltInThemes.DEFAULT_DARK.id }
                dataStore.updateData {
                    it
                        .toBuilder()
                        .setAppThemeId(lastStable)
                        .setThemeRollbackFlag(true)
                        .build()
                }
                resolveTheme(lastStable, isApp = true)
                _isRollbackFlagActive.value = true
            }
    }
