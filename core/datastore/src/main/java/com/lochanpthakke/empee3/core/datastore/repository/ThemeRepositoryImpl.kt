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

        private val _appThemeId = MutableStateFlow(BuiltInThemes.DEFAULT_DARK.id)
        override val appThemeId: StateFlow<String> = _appThemeId.asStateFlow()

        private val _glassEffectEnabled = MutableStateFlow(true)
        override val glassEffectEnabled: StateFlow<Boolean> = _glassEffectEnabled.asStateFlow()

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

        private val _customScanLocationUri = MutableStateFlow("")
        override val customScanLocationUri: StateFlow<String> = _customScanLocationUri.asStateFlow()

        private val _customScanLocationPath = MutableStateFlow("")
        override val customScanLocationPath: StateFlow<String> = _customScanLocationPath.asStateFlow()

        private val _isCustomScanLocationEnabled = MutableStateFlow(false)
        override val isCustomScanLocationEnabled: StateFlow<Boolean> = _isCustomScanLocationEnabled.asStateFlow()

        private val _scanLocationConfigured = MutableStateFlow(false)
        override val scanLocationConfigured: StateFlow<Boolean> = _scanLocationConfigured.asStateFlow()

        private val _defaultStartSection = MutableStateFlow("TRACKS")
        override val defaultStartSection: StateFlow<String> = _defaultStartSection.asStateFlow()

        private val _metadataProvider = MutableStateFlow("")
        override val metadataProvider: StateFlow<String> = _metadataProvider.asStateFlow()

        private val _metadataFixAction = MutableStateFlow("")
        override val metadataFixAction: StateFlow<String> = _metadataFixAction.asStateFlow()

        private val _metadataConfidenceThreshold = MutableStateFlow(0f)
        override val metadataConfidenceThreshold: StateFlow<Float> = _metadataConfidenceThreshold.asStateFlow()

        init {
            scope.launch {
                var proto = dataStore.data.first()
                proto.migratedThemeSelection()?.let { migrated ->
                    dataStore.updateData { it.migratedThemeSelection() ?: it }
                    proto = migrated
                }
                _glassEffectEnabled.value = if (proto.hasGlassEffectEnabled()) proto.glassEffectEnabled else true
                _libraryLayoutMode.value = proto.libraryLayoutMode.ifBlank { "GRID" }
                _isRollbackFlagActive.value = proto.themeRollbackFlag
                _customScanLocationUri.value = proto.customScanLocationUri
                _customScanLocationPath.value = proto.customScanLocationPath
                _isCustomScanLocationEnabled.value = proto.isCustomScanLocationEnabled
                _scanLocationConfigured.value = proto.scanLocationConfigured
                _defaultStartSection.value = proto.defaultStartSection.ifBlank { "TRACKS" }
                _metadataProvider.value = proto.metadataProvider
                _metadataFixAction.value = proto.metadataFixAction
                _metadataConfidenceThreshold.value = proto.metadataConfidenceThreshold
                resolveTheme(proto.appThemeId.ifBlank { BuiltInThemes.DEFAULT_DARK.id }, isApp = true)
                resolveTheme(proto.widgetThemeId.ifBlank { BuiltInThemes.DEFAULT_DARK.id }, isApp = false)
            }
        }

        private fun resolveTheme(
            id: String,
            isApp: Boolean,
        ) {
            val resolved = BuiltInThemes.resolve(id)
            if (isApp) {
                _appThemeId.value = id
                _appThemeState.value = resolved.theme
                _appSkinState.value = resolved.skin
            } else {
                _widgetThemeState.value = resolved.theme
                _widgetSkinState.value = resolved.skin
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

        override suspend fun setGlassEffectEnabled(enabled: Boolean): AppResult<Unit> =
            appResultOf {
                dataStore.updateData { it.toBuilder().setGlassEffectEnabled(enabled).build() }
                _glassEffectEnabled.value = enabled
            }

        override suspend fun setDefaultStartSection(section: String): AppResult<Unit> =
            appResultOf {
                dataStore.updateData { it.toBuilder().setDefaultStartSection(section).build() }
                _defaultStartSection.value = section
            }

        override suspend fun setMetadataProvider(provider: String): AppResult<Unit> =
            appResultOf {
                dataStore.updateData { it.toBuilder().setMetadataProvider(provider).build() }
                _metadataProvider.value = provider
            }

        override suspend fun setMetadataFixAction(action: String): AppResult<Unit> =
            appResultOf {
                dataStore.updateData { it.toBuilder().setMetadataFixAction(action).build() }
                _metadataFixAction.value = action
            }

        override suspend fun setMetadataConfidenceThreshold(threshold: Float): AppResult<Unit> =
            appResultOf {
                dataStore.updateData { it.toBuilder().setMetadataConfidenceThreshold(threshold).build() }
                _metadataConfidenceThreshold.value = threshold
            }

        override suspend fun setCustomScanLocation(
            uri: String,
            path: String,
        ): AppResult<Unit> =
            appResultOf {
                dataStore.updateData {
                    it
                        .toBuilder()
                        .setCustomScanLocationUri(uri)
                        .setCustomScanLocationPath(path)
                        .setIsCustomScanLocationEnabled(true)
                        .setScanLocationConfigured(true)
                        .build()
                }
                _customScanLocationUri.value = uri
                _customScanLocationPath.value = path
                _isCustomScanLocationEnabled.value = true
                _scanLocationConfigured.value = true
            }

        override suspend fun setScanEntireDevice(): AppResult<Unit> =
            appResultOf {
                dataStore.updateData {
                    it
                        .toBuilder()
                        .setIsCustomScanLocationEnabled(false)
                        .setScanLocationConfigured(true)
                        .build()
                }
                _isCustomScanLocationEnabled.value = false
                _scanLocationConfigured.value = true
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
                        .ifBlank { BuiltInThemes.SYSTEM_DEFAULT_ID }
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
