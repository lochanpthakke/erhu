package com.erhu.erhu.feature.settings.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.feature.settings.component.SettingsChoiceRow
import com.erhu.erhu.feature.settings.component.SettingsGroup
import com.erhu.erhu.feature.settings.component.SettingsPage
import com.erhu.erhu.feature.settings.component.SettingsSection

/** In-app language selector integrated with Android's per-app locale system. */
@Composable
fun LanguageSettingsSubPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedTag by remember { mutableStateOf(AppLocaleManager.selectedLanguageTag(context)) }
    val systemLanguageName = remember(context, selectedTag) { AppLocaleManager.systemLanguageDisplayName(context) }
    val systemSummary =
        if (systemLanguageName.isNotBlank()) {
            stringResource(R.string.settings_language_system_default_summary, systemLanguageName)
        } else {
            stringResource(R.string.settings_language_system_default_fallback)
        }

    SettingsPage(
        title = title,
        navigation = TopBarNavigation.Detail(onBack),
        modifier = modifier,
    ) {
        SettingsSection(
            title = stringResource(R.string.settings_language_section),
            footer = stringResource(R.string.settings_language_footer),
        ) {
            SettingsGroup(selectable = true) {
                row {
                    SettingsChoiceRow(
                        title = stringResource(R.string.settings_language_system_default),
                        summary = systemSummary,
                        selected = selectedTag == null,
                        onClick = {
                            AppLocaleManager.setLanguageTag(context, null)
                            selectedTag = null
                        },
                    )
                }
                AppLocaleManager.supportedLanguages.forEach { language ->
                    row {
                        SettingsChoiceRow(
                            title = language.nativeName,
                            summary = language.englishName.takeIf { it != language.nativeName },
                            selected = selectedTag == language.tag,
                            onClick = {
                                AppLocaleManager.setLanguageTag(context, language.tag)
                                selectedTag = language.tag
                            },
                        )
                    }
                }
            }
        }
    }
}
