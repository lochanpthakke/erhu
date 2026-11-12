package com.erhu.erhu.onboarding

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.erhu.erhu.R
import com.erhu.erhu.audioPermission
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.feature.settings.component.SettingsSwitchRow
import com.erhu.erhu.findActivity
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import com.erhu.erhu.core.common.R as CommonR

@Suppress("MagicNumber") // Onboarding palette, isolated from the app theme.
private val IntroductionBackground = Color(0xff0b0e11)

/** Local UI state is saveable; completion, VYBE visibility and glass use existing persisted settings. */
@Suppress("LongMethod", "CyclomaticComplexMethod") // Existing declarative navigation/permission tree.
@Composable
fun OnboardingScreen(
    vybeEnabled: Boolean,
    onVybeEnabledChange: (Boolean) -> Unit,
    onFinish: suspend () -> Boolean,
    onExit: () -> Unit,
    glassEffectEnabled: Boolean,
    onGlassEffectEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (lightStatus != null) controller.isAppearanceLightStatusBars = lightStatus
            if (lightNavigation != null) controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
    var resumed by remember { mutableStateOf(false) }
    var motionEnabled by remember { mutableStateOf(true) }
    LifecycleResumeEffect(context) {
        resumed = true
        motionEnabled = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        onPauseOrDispose { resumed = false }
    }
    val permission = audioPermission()

    fun permissionGranted() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var mediaGranted by remember { mutableStateOf(permissionGranted()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    fun refreshPermission() {
        mediaGranted = permissionGranted()
        permanentlyDenied = requested &&
            !mediaGranted &&
            context.findActivity()?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
            } == true
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPermission()
        }
    LifecycleResumeEffect(permission) {
        refreshPermission()
        onPauseOrDispose {}
    }
    val widgetsAvailable =
        remember(context) {
            runCatching {
                val receivers = setOf("MusicWidgetReceiver", "GlassMusicWidgetReceiver", "VinylMusicWidgetReceiver")
                val available =
                    AppWidgetManager
                        .getInstance(context)
                        .installedProviders
                        .filter { it.provider.packageName == context.packageName }
                        .map { it.provider.className.substringAfterLast('.') }
                        .toSet()
                available.containsAll(receivers)
            }.getOrDefault(false)
        }
    val pages = remember(vybeEnabled, widgetsAvailable) { introductionPages(vybeEnabled, widgetsAvailable) }
    val initialLanguageTag =
        remember(context) {
            val current = AppLocaleManager.selectedLanguageTag(context)
            if (current != null) {
                current
            } else {
                val sysLocale = AppLocaleManager.systemLocale(context)
                val detected =
                    AppLocaleManager.findSupportedTag(sysLocale.toLanguageTag())
                        ?: AppLocaleManager.findSupportedTag(sysLocale.language)
                        ?: "en"
                AppLocaleManager.setLanguageTag(context, detected)
                detected
            }
        }
    var selectedLanguageTag by rememberSaveable { mutableStateOf(initialLanguageTag) }
    var savedPage by rememberSaveable { mutableStateOf(IntroductionPage.LANGUAGE.name) }
    var finishing by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val finish: () -> Unit = {
        if (!finishing) {
            finishing = true
            saveError = false
            scope.launch {
                if (!onFinish()) {
                    finishing = false
                    saveError = true
                }
            }
        }
    }
    val colors =
        darkColorScheme(
            primary = Color(0xfff3f2ee),
            onPrimary = IntroductionBackground,
            secondary = Color(0xfff3f2ee),
            onSecondary = IntroductionBackground,
            background = IntroductionBackground,
            surface = Color(0xff191b1d),
            onSurface = Color(0xfff3f2ee),
            onSurfaceVariant = Color(0xffb7bab7),
            secondaryContainer = Color(0xff35383a),
            onSecondaryContainer = Color.White,
            outline = Color(0xff45494a),
        )
    MaterialTheme(colorScheme = colors) {
        Surface(color = IntroductionBackground, contentColor = colors.onSurface) {
            // Recreate only when available page identities change, preserving the current page's identity.
            key(pages) {
                val pager = rememberPagerState(initialPage = restoredIntroductionPage(savedPage, pages), pageCount = { pages.size })
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.collect { savedPage = pages[it].name }
                }
                val previous: () -> Unit = {
                    if (!finishing) {
                        if (pager.targetPage == 0) {
                            onExit()
                        } else {
                            scope.launch {
                                pager.animateScrollToPage(
                                    (pager.targetPage - 1).coerceAtLeast(0),
                                    animationSpec = tween(Motion.CONTENT, easing = Motion.Standard),
                                )
                            }
                        }
                    }
                }
                BackHandler(onBack = previous)
                var entered by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { entered = true }
                val entrance by animateFloatAsState(if (entered) 1f else 0f, tween(Motion.CONTENT), label = "introductionEntrance")
                Column(
                    modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(IntroductionBackground, Color(0xff131a20), IntroductionBackground)))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .graphicsLayer { alpha = entrance },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Image(painterResource(R.drawable.erhu_logo), null, Modifier.size(32.dp))
                            Image(
                                painterResource(R.drawable.onb_wordmark),
                                stringResource(R.string.app_name),
                                Modifier.width(82.dp).height(32.dp),
                            )
                        }
                        TextButton(onClick = finish, enabled = !finishing) { Text(stringResource(CommonR.string.action_skip)) }
                    }
                    HorizontalPager(
                        state = pager,
                        key = { pages[it].name },
                        userScrollEnabled = !finishing,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) { index ->
                        val page = pages[index]
                        IntroductionContent(
                            page = page,
                            active = resumed && pager.settledPage == index && !pager.isScrollInProgress,
                            motionEnabled = motionEnabled,
                            pageOffset = { ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue },
                            mediaGranted = mediaGranted,
                            requested = requested,
                            permanentlyDenied = permanentlyDenied,
                            onRequestPermission = {
                                requested = true
                                permissionLauncher.launch(permission)
                            },
                            onOpenSettings = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ),
                                )
                            },
                            vybeEnabled = vybeEnabled,
                            onVybeEnabledChange = onVybeEnabledChange,
                            glassEffectEnabled = glassEffectEnabled,
                            onGlassEffectEnabledChange = onGlassEffectEnabledChange,
                            selectedLanguageTag = selectedLanguageTag,
                            onLanguageSelected = { tag ->
                                selectedLanguageTag = tag
                                AppLocaleManager.setLanguageTag(context, tag)
                            },
                        )
                    }
                    Surface(color = Color(0xed11161b), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    pages.indices.forEach { i ->
                                        val emphasis by animateFloatAsState(
                                            if (i == pager.currentPage) 1f else .35f,
                                            tween(Motion.MICRO),
                                            label = "pageIndicator",
                                        )
                                        Box(
                                            Modifier
                                                .width(12.dp)
                                                .height(4.dp)
                                                .graphicsLayer {
                                                    scaleX = emphasis
                                                    alpha = emphasis
                                                }.background(Color.White, CircleShape),
                                        )
                                    }
                                }
                                Text(
                                    stringResource(CommonR.string.onboarding_page_indicator, pager.currentPage + 1, pages.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (saveError) {
                                Text(
                                    stringResource(CommonR.string.onboarding_save_error),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = previous,
                                    enabled = pager.targetPage > 0 && !finishing,
                                ) { Text(stringResource(CommonR.string.action_back)) }
                                Spacer(Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        if (pager.settledPage == pages.lastIndex) {
                                            finish()
                                        } else {
                                            scope.launch {
                                                pager.animateScrollToPage(
                                                    (pager.targetPage + 1).coerceAtMost(pages.lastIndex),
                                                    animationSpec = tween(Motion.CONTENT, easing = Motion.Standard),
                                                )
                                            }
                                        }
                                    },
                                    enabled = !finishing,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        stringResource(
                                            when {
                                                finishing -> CommonR.string.action_saving
                                                pager.settledPage == pages.lastIndex -> CommonR.string.onboarding_start_listening
                                                pager.settledPage == 0 && pages.firstOrNull() == IntroductionPage.LANGUAGE ->
                                                    CommonR.string.action_continue
                                                else -> CommonR.string.action_next
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList") // Keep the existing page explanations and callbacks together.
@Composable
private fun IntroductionContent(
    page: IntroductionPage,
    active: Boolean,
    motionEnabled: Boolean,
    pageOffset: () -> Float,
    mediaGranted: Boolean,
    requested: Boolean,
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    vybeEnabled: Boolean,
    onVybeEnabledChange: (Boolean) -> Unit,
    glassEffectEnabled: Boolean,
    onGlassEffectEnabledChange: (Boolean) -> Unit,
    selectedLanguageTag: String,
    onLanguageSelected: (String) -> Unit,
) {
    val toolGuide = remember(page) { page.toolGuide() }
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val offset = if (motionEnabled) pageOffset().coerceIn(0f, 1f) else 0f
                alpha = 1f - offset * .18f
                translationY = offset * 6.dp.toPx()
            }.verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(page.titleRes),
            style =
                if (page == IntroductionPage.WELCOME ||
                    page == IntroductionPage.READY ||
                    page == IntroductionPage.LANGUAGE
                ) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.headlineLarge
                },
            fontWeight = FontWeight.SemiBold,
            modifier =
                Modifier.semantics {
                    heading()
                },
        )
        Text(
            stringResource(
                if (page == IntroductionPage.READY &&
                    !mediaGranted
                ) {
                    CommonR.string.onboarding_library_later_hint
                } else {
                    page.descriptionRes
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (page == IntroductionPage.LANGUAGE) {
            LanguageSelectionContent(
                selectedTag = selectedLanguageTag,
                onLanguageSelected = onLanguageSelected,
            )
        }
        if (page == IntroductionPage.READY) {
            IntroductionVisual(page, null, active, motionEnabled)
        }
        if (page == IntroductionPage.LIBRARY) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xff212426)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            if (mediaGranted) {
                                CommonR.string.onboarding_permission_allowed
                            } else {
                                CommonR.string.onboarding_permission_private_title
                            },
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(
                            when {
                                mediaGranted -> CommonR.string.onboarding_permission_summary_granted
                                permanentlyDenied -> CommonR.string.onboarding_permission_summary_denied
                                requested -> CommonR.string.onboarding_permission_summary_retry
                                else -> CommonR.string.onboarding_permission_summary_default
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!mediaGranted) {
                        OutlinedButton(onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission) {
                            Text(
                                stringResource(
                                    if (permanentlyDenied) {
                                        CommonR.string.onboarding_permission_open_settings
                                    } else if (requested) {
                                        CommonR.string.onboarding_permission_retry
                                    } else {
                                        CommonR.string.onboarding_permission_allow
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
        if (page == IntroductionPage.VYBE || page == IntroductionPage.READY) {
            SettingsSwitchRow(
                title = stringResource(CommonR.string.onboarding_vybe_title),
                summary = stringResource(CommonR.string.onboarding_vybe_summary),
                checked = vybeEnabled,
                onCheckedChange = onVybeEnabledChange,
            )
        }
        if (page == IntroductionPage.READY) {
            SettingsSwitchRow(
                title = stringResource(CommonR.string.onboarding_glass_title),
                summary = stringResource(CommonR.string.onboarding_glass_summary),
                checked = glassEffectEnabled,
                onCheckedChange = onGlassEffectEnabledChange,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CheckCircle, null)
                Text(
                    stringResource(
                        if (mediaGranted) {
                            CommonR.string.onboarding_permission_allowed
                        } else {
                            CommonR.string.onboarding_permission_badge_later
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(CommonR.string.onboarding_ready_choose_song),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xff212426)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(CommonR.string.onboarding_beta_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(CommonR.string.onboarding_beta_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (page == IntroductionPage.LANGUAGE) {
            // Language selection page has no preview gallery
        } else if (toolGuide != null) {
            PreviewGallery(page, active, motionEnabled)
            ToolGuideContent(page, toolGuide)
        } else {
            PreviewGallery(page, active, motionEnabled)
        }
        if (page == IntroductionPage.VYBE) {
            Text(
                stringResource(CommonR.string.onboarding_vybe_pack_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewGallery(
    page: IntroductionPage,
    active: Boolean,
    motionEnabled: Boolean,
) {
    val previews = remember(page) { page.previews() }
    var selected by rememberSaveable(page.name) { mutableIntStateOf(0) }
    val shown = previews[selected.coerceIn(previews.indices)]
    if (previews.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            previews.forEachIndexed { index, preview ->
                FilterChip(
                    selected = selected == index,
                    onClick = { selected = index },
                    label = { Text(stringResource(preview.labelRes)) },
                )
            }
        }
    }
    IntroductionVisual(page, shown, active, motionEnabled)
    Text(
        stringResource(CommonR.string.onboarding_preview_existing_library),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LanguageSelectionContent(
    selectedTag: String,
    onLanguageSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLocaleManager.supportedLanguages.forEach { language ->
            LanguageSelectionItem(
                language = language,
                isSelected = language.tag == selectedTag,
                onClick = { onLanguageSelected(language.tag) },
            )
        }
    }
}

@Composable
private fun LanguageSelectionItem(
    language: com.erhu.erhu.core.common.locale.SupportedLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xff2d3238) else Color(0xff181b1e),
        border =
            BorderStroke(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = language.nativeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (language.englishName != language.nativeName) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = language.englishName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            LanguageSelectionIndicator(isSelected)
        }
    }
}

@Composable
private fun LanguageSelectionIndicator(isSelected: Boolean) {
    if (isSelected) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    } else {
        Box(
            modifier =
                Modifier
                    .size(20.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        )
    }
}
