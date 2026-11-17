package com.erhu.erhu.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.erhu.erhu.MainScaffold
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.feature.settings.theme.ThemeViewModel
import com.erhu.erhu.findActivity

/** Wait for persisted completion before composing either navigation tree. Playback remains independent. */
@Composable
fun AppEntry(viewModel: ThemeViewModel = hiltViewModel()) {
    val persistedSetup by viewModel.navigationSetup.collectAsState()
    val setup = persistedSetup
    val glassEffectEnabled by viewModel.glassEffectEnabled.collectAsState()
    var startAtHome by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    when {
        setup == null -> Box(Modifier.fillMaxSize().background(Color(0xff101112)))
        setup.introductionCompleted && setup.shortcuts.isNotEmpty() ->
            com.erhu.erhu.announcements.AnnouncementHost { openAnnouncements ->
                MainScaffold(startAtHome = startAtHome, onOpenAnnouncements = openAnnouncements)
            }
        else ->
            OnboardingScreen(
                vybeEnabled = setup.vybeEnabled,
                onVybeEnabledChange = viewModel::setVybeEnabled,
                glassEffectEnabled = glassEffectEnabled,
                onGlassEffectEnabledChange = viewModel::setGlassEffectEnabled,
                onExit = { context.findActivity()?.finish() },
                quickAccessOnly = setup.introductionCompleted,
                legacyStartSection = setup.defaultStartSection,
                onConfirmShortcuts = { shortcuts ->
                    startAtHome = !setup.introductionCompleted
                    viewModel.saveNavigationQuickAccess(shortcuts, completeIntroduction = !setup.introductionCompleted) is AppResult.Success
                },
            )
    }
}
