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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.erhu.erhu.R
import com.erhu.erhu.audioPermission
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.feature.settings.component.SettingsSwitchRow
import com.erhu.erhu.findActivity
import kotlinx.coroutines.launch

private val IntroductionBackground = Color(0xff101112)

/** Local UI state is saveable; only completion and optional VYBE visibility are persisted settings. */
@Composable
fun OnboardingScreen(
    vybeEnabled: Boolean,
    onVybeEnabledChange: (Boolean) -> Unit,
    onFinish: suspend () -> Boolean,
    onExit: () -> Unit,
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
    var savedPage by rememberSaveable { mutableStateOf(IntroductionPage.WELCOME.name) }
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
                    if (!finishing && !pager.isScrollInProgress) {
                        if (pager.currentPage == 0) {
                            onExit()
                        } else {
                            scope.launch {
                                pager.animateScrollToPage(
                                    pager.currentPage - 1,
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
                        .background(IntroductionBackground)
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
                            Image(painterResource(R.drawable.onb_wordmark), "erhu", Modifier.width(82.dp).height(32.dp))
                        }
                        TextButton(onClick = finish, enabled = !finishing) { Text("Skip") }
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
                        )
                    }
                    Surface(color = Color(0xff17191b), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    pages.indices.forEach { i ->
                                        Box(
                                            Modifier.size(6.dp).background(
                                                if (i ==
                                                    pager.currentPage
                                                ) {
                                                    Color.White
                                                } else {
                                                    Color(0xff505455)
                                                },
                                                CircleShape,
                                            ),
                                        )
                                    }
                                }
                                Text(
                                    "${pager.currentPage + 1} / ${pages.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (saveError) {
                                Text(
                                    "Couldn't save your setup. Try again.",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = previous,
                                    enabled = pager.currentPage > 0 && !finishing && !pager.isScrollInProgress,
                                ) { Text("Back") }
                                Spacer(Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        if (pager.currentPage == pages.lastIndex) {
                                            finish()
                                        } else {
                                            scope.launch {
                                                pager.animateScrollToPage(
                                                    pager.currentPage + 1,
                                                    animationSpec = tween(Motion.CONTENT, easing = Motion.Standard),
                                                )
                                            }
                                        }
                                    },
                                    enabled = !finishing && !pager.isScrollInProgress,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        if (finishing) {
                                            "Saving…"
                                        } else if (pager.currentPage == pages.lastIndex) {
                                            "Start Listening"
                                        } else {
                                            "Next"
                                        },
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

@Composable
private fun IntroductionContent(
    page: IntroductionPage,
    mediaGranted: Boolean,
    requested: Boolean,
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    vybeEnabled: Boolean,
    onVybeEnabledChange: (Boolean) -> Unit,
) {
    val toolGuide = remember(page) { page.toolGuide() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            page.title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            modifier =
                Modifier.semantics {
                    heading()
                },
        )
        Text(
            if (page == IntroductionPage.READY &&
                !mediaGranted
            ) {
                "You can add music access later. Make yourself at home."
            } else {
                page.description
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (page == IntroductionPage.LIBRARY) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xff212426)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (mediaGranted) "Music access allowed" else "Your files stay yours", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            mediaGranted -> "Choose a scan location when you open the library."
                            permanentlyDenied -> "Music access is off in Android settings. Enable it there, then return."
                            requested -> "Music access wasn't granted. Retry, or continue and add it later."
                            else -> "Allow audio-file access so erhu can build your library. You can continue without it."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!mediaGranted) {
                        OutlinedButton(onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission) {
                            Text(
                                if (permanentlyDenied) {
                                    "Open Android settings"
                                } else if (requested) {
                                    "Retry music access"
                                } else {
                                    "Allow music access"
                                },
                            )
                        }
                    }
                }
            }
        }
        if (page == IntroductionPage.VYBE || page == IntroductionPage.READY) {
            SettingsSwitchRow(
                title = "Enable VYBE",
                summary = "Show VYBE in navigation and Home.",
                checked = vybeEnabled,
                onCheckedChange = onVybeEnabledChange,
            )
        }
        if (page == IntroductionPage.READY) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CheckCircle, null)
                Text(
                    if (mediaGranted) "Music access allowed" else "Music access can be added later",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "Choose a song when you are ready.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (toolGuide != null) {
            PreviewGallery(page)
            ToolGuideContent(page, toolGuide)
        } else {
            PreviewGallery(page)
        }
        if (page == IntroductionPage.VYBE) {
            Text(
                "VYBE search needs the separate model pack. You can set it up later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewGallery(page: IntroductionPage) {
    val previews = remember(page) { page.previews() }
    var selected by rememberSaveable(page.name) { mutableIntStateOf(0) }
    val shown = previews[selected.coerceIn(previews.indices)]
    if (previews.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            previews.forEachIndexed { index, preview ->
                FilterChip(selected = selected == index, onClick = { selected = index }, label = { Text(preview.label) })
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (page == IntroductionPage.LIBRARY) 180.dp else 260.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xff191b1c))
            .border(1.dp, Color(0xff333738), RoundedCornerShape(24.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(shown.image), "${shown.label} preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
    Text(
        "Preview from an existing erhu library.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
