package com.erhu.erhu.feature.settings.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.datastore.repository.ThemeRepository
import com.erhu.erhu.core.designsystem.skin.SkinManifest
import com.erhu.erhu.core.designsystem.theme.ThemeManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        private val themeRepository: ThemeRepository,
    ) : ViewModel() {
        val appTheme: StateFlow<ThemeManifest> = themeRepository.appThemeState
        val appThemeId: StateFlow<String> = themeRepository.appThemeId
        val glassEffectEnabled: StateFlow<Boolean> = themeRepository.glassEffectEnabled
        val appSkin: StateFlow<SkinManifest?> = themeRepository.appSkinState
        val widgetTheme: StateFlow<ThemeManifest> = themeRepository.widgetThemeState
        val widgetSkin: StateFlow<SkinManifest?> = themeRepository.widgetSkinState
        val libraryLayout: StateFlow<String> = themeRepository.libraryLayoutMode
        val isRollbackActive: StateFlow<Boolean> = themeRepository.isRollbackFlagActive
        val vybeEnabled = themeRepository.vybeEnabled
        val onboardingCompleted = themeRepository.onboardingCompleted

        suspend fun completeOnboarding(): AppResult<Unit> = themeRepository.completeOnboarding()

        fun setVybeEnabled(enabled: Boolean) {
            viewModelScope.launch { themeRepository.setVybeEnabled(enabled) }
        }

        val defaultStartSection: StateFlow<String> = themeRepository.defaultStartSection
        val sidebarGlassyness: StateFlow<Float> = themeRepository.sidebarGlassyness
        val uiGlassyness: StateFlow<Float> = themeRepository.uiGlassyness
        val popupGlassOpacity: StateFlow<Float> = themeRepository.popupGlassOpacity

        private val _importMessage = MutableStateFlow<String?>(null)
        val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

        fun selectAppTheme(themeId: String) {
            viewModelScope.launch {
                themeRepository.setAppTheme(themeId)
            }
        }

        fun selectWidgetTheme(themeId: String) {
            viewModelScope.launch {
                themeRepository.setWidgetTheme(themeId)
            }
        }

        fun setGlassEffectEnabled(enabled: Boolean) {
            viewModelScope.launch {
                themeRepository.setGlassEffectEnabled(enabled)
            }
        }

        fun setSidebarGlassyness(value: Float) {
            viewModelScope.launch {
                themeRepository.setSidebarGlassyness(value)
            }
        }

        fun setUiGlassyness(value: Float) {
            viewModelScope.launch {
                themeRepository.setUiGlassyness(value)
            }
        }

        private var popupOpacityUpdate: Job? = null

        fun setPopupGlassOpacity(value: Float) {
            popupOpacityUpdate?.cancel()
            popupOpacityUpdate = viewModelScope.launch { themeRepository.setPopupGlassOpacity(value) }
        }

        fun selectLibraryLayout(mode: String) {
            viewModelScope.launch {
                themeRepository.setLibraryLayoutMode(mode)
            }
        }

        fun setDefaultStartSection(section: String) {
            viewModelScope.launch {
                themeRepository.setDefaultStartSection(section)
            }
        }

        fun importPackage(inputStream: InputStream) {
            viewModelScope.launch {
                val result = themeRepository.importPackage(inputStream)
                when (result) {
                    is AppResult.Success -> {
                        val data = result.data
                        _importMessage.value = "Successfully imported '${data.name}'!"
                        if (data.isSkin) {
                            themeRepository.setAppTheme(data.themeId)
                        } else {
                            themeRepository.setAppTheme(data.themeId)
                        }
                    }
                    is AppResult.Error -> {
                        _importMessage.value = "Import failed: ${result.error.message}"
                    }
                }
            }
        }

        fun clearImportMessage() {
            _importMessage.value = null
        }

        fun acknowledgeRollback() {
            viewModelScope.launch {
                themeRepository.acknowledgeRollback()
            }
        }
    }
