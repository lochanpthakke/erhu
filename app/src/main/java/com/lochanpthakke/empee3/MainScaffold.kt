package com.lochanpthakke.empee3

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.lochanpthakke.empee3.core.designsystem.component.LocalBottomChromeHeight
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.foundation.LocalSharedTransitionScope
import com.lochanpthakke.empee3.core.designsystem.foundation.ProvideMotionScope
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.BuiltInThemes
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Theme
import com.lochanpthakke.empee3.core.designsystem.theme.GlassLevel
import com.lochanpthakke.empee3.core.designsystem.theme.LocalGlassBackdrop
import com.lochanpthakke.empee3.core.designsystem.theme.glassBackdropSource
import com.lochanpthakke.empee3.core.designsystem.theme.glassChromeFade
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.designsystem.theme.rememberGlassBackdrop
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.feature.library.AddToPlaylistDialog
import com.lochanpthakke.empee3.feature.library.AlbumDetailScreen
import com.lochanpthakke.empee3.feature.library.ArtistDetailScreen
import com.lochanpthakke.empee3.feature.library.ArtworkManagerScreen
import com.lochanpthakke.empee3.feature.library.LibraryBottomChrome
import com.lochanpthakke.empee3.feature.library.LibraryScreen
import com.lochanpthakke.empee3.feature.library.LibrarySection
import com.lochanpthakke.empee3.feature.library.LibraryViewModel
import com.lochanpthakke.empee3.feature.library.LocalPlayerTransition
import com.lochanpthakke.empee3.feature.library.MetadataEditorScreen
import com.lochanpthakke.empee3.feature.library.NowPlayingScreen
import com.lochanpthakke.empee3.feature.library.PlaylistDetailScreen
import com.lochanpthakke.empee3.feature.library.PlaylistsViewModel
import com.lochanpthakke.empee3.feature.library.collectStablePlaybackState
import com.lochanpthakke.empee3.feature.library.rememberLibraryChromeState
import com.lochanpthakke.empee3.feature.library.rememberPlayerTransitionState
import com.lochanpthakke.empee3.feature.settings.SettingsCategoryScreen
import com.lochanpthakke.empee3.feature.settings.SettingsScreen
import com.lochanpthakke.empee3.feature.settings.diagnostics.DiagnosticsViewModel
import com.lochanpthakke.empee3.feature.settings.sound.SoundSettingsScreen
import com.lochanpthakke.empee3.feature.settings.sound.SoundViewModel
import com.lochanpthakke.empee3.feature.settings.theme.ThemeRollbackDialog
import com.lochanpthakke.empee3.feature.settings.theme.ThemeViewModel
import com.lochanpthakke.empee3.navigation.AlbumDetail
import com.lochanpthakke.empee3.navigation.ArtistDetail
import com.lochanpthakke.empee3.navigation.ArtworkManager
import com.lochanpthakke.empee3.navigation.Equalizer
import com.lochanpthakke.empee3.navigation.Library
import com.lochanpthakke.empee3.navigation.MetadataEditor
import com.lochanpthakke.empee3.navigation.PlaylistDetail
import com.lochanpthakke.empee3.navigation.Settings
import com.lochanpthakke.empee3.navigation.SettingsCategory
import com.lochanpthakke.empee3.navigation.navEnter
import com.lochanpthakke.empee3.navigation.navExit
import com.lochanpthakke.empee3.navigation.navPopEnter
import com.lochanpthakke.empee3.navigation.navPopExit
import com.lochanpthakke.empee3.ui.components.ExpandingPlayer
import com.lochanpthakke.empee3.ui.components.MiniPlayer
import com.lochanpthakke.empee3.ui.components.WriteAccessRequestEffect
import kotlinx.coroutines.launch
import android.provider.Settings as AndroidSettings

private class DrawerDestination(
    val label: String,
    val icon: ImageVector,
    val route: Any,
)

private val DRAWER_DESTINATIONS =
    listOf(
        DrawerDestination("Library", Icons.AutoMirrored.Filled.LibraryBooks, Library),
        DrawerDestination("Equalizer", Icons.Default.Equalizer, Equalizer),
        DrawerDestination("Metadata Editor", Icons.Default.EditNote, MetadataEditor),
        DrawerDestination("Settings", Icons.Default.Settings, Settings),
    )

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun MainScaffold() {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val soundViewModel: SoundViewModel = hiltViewModel()
    val diagnosticsViewModel: DiagnosticsViewModel = hiltViewModel()
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val playlistsViewModel: PlaylistsViewModel = hiltViewModel()
    val playbackRepository = libraryViewModel.playbackRepository

    val selectedTheme by themeViewModel.appTheme.collectAsState()
    val selectedThemeId by themeViewModel.appThemeId.collectAsState()
    val glassEffectEnabled by themeViewModel.glassEffectEnabled.collectAsState()
    // System Default follows the device's light/dark setting (re-read on configuration changes).
    val isSystemDark = isSystemInDarkTheme()
    val currentTheme =
        if (selectedThemeId == BuiltInThemes.SYSTEM_DEFAULT_ID) BuiltInThemes.forSystemAppearance(isSystemDark) else selectedTheme
    val currentSkin by themeViewModel.appSkin.collectAsState()
    val isRollbackActive by themeViewModel.isRollbackActive.collectAsState()

    if (isRollbackActive) {
        ThemeRollbackDialog(onDismiss = { themeViewModel.acknowledgeRollback() })
    }

    // Tag edits from the library or the metadata editor may need write access first.
    val writeAccessRequest by libraryViewModel.writeAccessRequest.collectAsState()
    WriteAccessRequestEffect(request = writeAccessRequest, onResult = libraryViewModel::onWriteAccessResult)

    EmPee3Theme(
        themeManifest = currentTheme,
        skinManifest = currentSkin,
        glassEffectEnabled = glassEffectEnabled,
    ) {
        // Content is drawn behind transparent system bars, so their icon colour must
        // follow the app theme rather than the system's light/dark setting.
        val view = LocalView.current
        val isDarkTheme = currentTheme.isDark
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDarkTheme
                    isAppearanceLightNavigationBars = !isDarkTheme
                }
            }
        }

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        // Now Playing is a sheet over the shell (not a destination), so dragging it down
        // reveals the live screen underneath.
        val player = rememberPlayerTransitionState()
        val isOnLibrary = currentDestination?.hasRoute(Library::class) == true

        // Library section lives here because its navigation is part of the shell's bottom chrome.
        var librarySection by rememberSaveable { mutableStateOf(LibrarySection.TRACKS) }
        // Search is opened from the bottom controls, so its on/off state lives here too.
        var isLibrarySearchActive by rememberSaveable { mutableStateOf(false) }
        // Library scrolling compacts the bottom chrome; switching section or searching restores it.
        val libraryChrome = rememberLibraryChromeState()
        LaunchedEffect(librarySection, isLibrarySearchActive) { libraryChrome.expand() }
        val context = LocalContext.current
        var isLibraryContentShown by rememberSaveable {
            mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED)
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
        val openPlayer: () -> Unit = { scope.launch { player.expand() } }
        val minimizePlayer: () -> Unit = { scope.launch { player.collapse() } }

        var songForPlaylist by remember { mutableStateOf<Song?>(null) }

        // Glass backdrops: the screens frost under the bottom chrome; the whole
        // shell frosts under the drawer.
        val screensBackdrop = rememberGlassBackdrop()
        val shellBackdrop = rememberGlassBackdrop()

        // Opened by the menu button only: horizontal edge swipes are reserved for the
        // player. Once open, it can still be swiped or tapped closed.
        CompositionLocalProvider(LocalPlayerTransition provides player) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier =
                            Modifier.glassPanel(
                                shape = DrawerDefaults.shape,
                                level = GlassLevel.Strong,
                                elevated = false,
                                backdrop = shellBackdrop,
                            ),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.xl),
                        ) {
                            Text(
                                text = "empee3",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Local Music Engine",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        DRAWER_DESTINATIONS.forEach { destination ->
                            NavigationDrawerItem(
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.label) },
                                selected =
                                    currentDestination?.hasRoute(destination.route::class) == true ||
                                        (destination.route == Settings && currentDestination?.hasRoute(SettingsCategory::class) == true),
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigateTopLevel(destination.route)
                                },
                                modifier = Modifier.padding(horizontal = Spacing.md),
                            )
                        }
                    }
                },
            ) {
                // Inset ownership: this shell owns the bottom edge. Its bottom bar is the
                // mini player plus a spacer for the gesture/navigation bar, and the
                // measured height of that chrome is handed to screens. Screens draw edge
                // to edge (no padding here) and their top bars own the status bar.
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Scaffold(
                                modifier = Modifier.glassBackdropSource(shellBackdrop),
                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                bottomBar = {
                                    CompositionLocalProvider(LocalGlassBackdrop provides screensBackdrop) {
                                        // Under glass, content scrolls beneath the chrome; the fade keeps it legible.
                                        // Stack: mini player, then (on Library) the section navigation, then the gesture area.
                                        Column(modifier = Modifier.glassChromeFade()) {
                                            // Mini player + (on the Library) sections and search, as one chrome
                                            // that compacts while the library scrolls.
                                            LibraryBottomChrome(
                                                state = libraryChrome,
                                                showNavigation = isOnLibrary && isLibraryContentShown,
                                                selected = librarySection,
                                                onSelect = { librarySection = it },
                                                onSearch = { isLibrarySearchActive = true },
                                                onExpand = { scope.launch { libraryChrome.expand() } },
                                                searching = isLibrarySearchActive,
                                                miniPlayer = { compact ->
                                                    // The collapsed form of the player; the expanding player grows out of it.
                                                    MiniPlayer(
                                                        playbackRepository = playbackRepository,
                                                        onMiniPlayerClick = {
                                                            openPlayer()
                                                        },
                                                        compact = compact,
                                                    )
                                                },
                                            )
                                            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                                        }
                                    }
                                },
                            ) { innerPadding ->
                                CompositionLocalProvider(LocalBottomChromeHeight provides innerPadding.calculateBottomPadding()) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = Library,
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .glassBackdropSource(screensBackdrop),
                                        enterTransition = { navEnter(targetState.destination) },
                                        exitTransition = { navExit(targetState.destination) },
                                        popEnterTransition = { navPopEnter(initialState.destination) },
                                        popExitTransition = { navPopExit(initialState.destination) },
                                    ) {
                                        composable<Library> {
                                            ProvideMotionScope(this) {
                                                LibraryRoute(
                                                    libraryViewModel = libraryViewModel,
                                                    playlistsViewModel = playlistsViewModel,
                                                    section = librarySection,
                                                    onOpenDrawer = openDrawer,
                                                    onContentShownChange = { isLibraryContentShown = it },
                                                    onOpenAlbum = { navController.navigate(AlbumDetail(it.id)) },
                                                    onOpenArtist = { navController.navigate(ArtistDetail(it.id)) },
                                                    onOpenPlaylist = { navController.navigate(PlaylistDetail(it)) },
                                                    isSearchActive = isLibrarySearchActive,
                                                    onSearchActiveChange = { isLibrarySearchActive = it },
                                                    chromeScroll = libraryChrome.connection,
                                                )
                                            }
                                        }
                                        composable<AlbumDetail> { entry ->
                                            ProvideMotionScope(this) {
                                                val route = entry.toRoute<AlbumDetail>()
                                                AlbumDetailScreen(
                                                    albumId = route.albumId,
                                                    viewModel = libraryViewModel,
                                                    playlistsViewModel = playlistsViewModel,
                                                    onBack = { navController.popBackStack() },
                                                )
                                            }
                                        }
                                        composable<ArtistDetail> { entry ->
                                            ProvideMotionScope(this) {
                                                val route = entry.toRoute<ArtistDetail>()
                                                ArtistDetailScreen(
                                                    artistId = route.artistId,
                                                    viewModel = libraryViewModel,
                                                    playlistsViewModel = playlistsViewModel,
                                                    onBack = { navController.popBackStack() },
                                                    onOpenAlbum = { navController.navigate(AlbumDetail(it.id)) },
                                                )
                                            }
                                        }
                                        composable<PlaylistDetail> { entry ->
                                            ProvideMotionScope(this) {
                                                val route = entry.toRoute<PlaylistDetail>()
                                                PlaylistDetailScreen(
                                                    playlistId = route.playlistId,
                                                    viewModel = playlistsViewModel,
                                                    libraryViewModel = libraryViewModel,
                                                    onBack = { navController.popBackStack() },
                                                )
                                            }
                                        }
                                        composable<Equalizer> {
                                            ProvideMotionScope(this) {
                                                SoundSettingsScreen(
                                                    viewModel = soundViewModel,
                                                    title = "Equalizer",
                                                    navigation = TopBarNavigation.TopLevel(openDrawer),
                                                )
                                            }
                                        }
                                        composable<MetadataEditor> {
                                            ProvideMotionScope(this) {
                                                MetadataEditorScreen(
                                                    viewModel = libraryViewModel,
                                                    onOpenDrawer = openDrawer,
                                                    onNavigateToMetadataSettings = {
                                                        navController.navigate(SettingsCategory("metadata"))
                                                    },
                                                    onNavigateToLibrary = {
                                                        navController.navigateTopLevel(Library)
                                                    },
                                                )
                                            }
                                        }
                                        composable<ArtworkManager> {
                                            ProvideMotionScope(this) {
                                                ArtworkManagerScreen(
                                                    viewModel = libraryViewModel,
                                                    onOpenDrawer = openDrawer,
                                                    onNavigateToMetadataSettings = {
                                                        navController.navigate(SettingsCategory("metadata"))
                                                    },
                                                    onNavigateToLibrary = {
                                                        navController.navigate(Library)
                                                    },
                                                )
                                            }
                                        }
                                        composable<Settings> {
                                            ProvideMotionScope(this) {
                                                SettingsScreen(
                                                    onOpenDrawer = openDrawer,
                                                    onOpenCategory = { navController.navigate(SettingsCategory(it)) },
                                                )
                                            }
                                        }
                                        composable<SettingsCategory> { entry ->
                                            ProvideMotionScope(this) {
                                                val route = entry.toRoute<SettingsCategory>()
                                                SettingsCategoryScreen(
                                                    categoryId = route.categoryId,
                                                    onBack = { navController.popBackStack() },
                                                    onNavigateToMetadataEditor = {
                                                        navController.navigate(MetadataEditor)
                                                    },
                                                    themeViewModel = themeViewModel,
                                                    soundViewModel = soundViewModel,
                                                    diagnosticsViewModel = diagnosticsViewModel,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // Composed after the NavHost so Back closes an open drawer before popping a screen.
                            BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
                            val playerState by playbackRepository.collectStablePlaybackState()
                            ExpandingPlayer(state = player, song = playerState.currentSong) {
                                val isFavorite by libraryViewModel.currentSongIsFavorite.collectAsState()
                                val lyrics by libraryViewModel.currentSongLyrics.collectAsState()
                                NowPlayingScreen(
                                    playbackRepository = playbackRepository,
                                    isFavorite = isFavorite,
                                    lyrics = lyrics,
                                    onBackClick = minimizePlayer,
                                    onToggleFavorite = { libraryViewModel.toggleFavorite(it) },
                                    onAddToPlaylist = { songForPlaylist = it },
                                )
                            }
                        }
                    }
                }
            }
        }

        songForPlaylist?.let { song ->
            AddToPlaylistDialog(
                song = song,
                viewModel = playlistsViewModel,
                onDismiss = { songForPlaylist = null },
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private enum class PermissionUiState {
    GRANTED,
    NOT_REQUESTED,
    DENIED,
    PERMANENTLY_DENIED,
}

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
@Suppress("LongMethod")
private fun LibraryRoute(
    libraryViewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    section: LibrarySection,
    onOpenDrawer: () -> Unit,
    onContentShownChange: (Boolean) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    chromeScroll: NestedScrollConnection,
) {
    val context = LocalContext.current
    val permission = audioPermission()

    fun isGranted() =
        ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED

    var permissionState by rememberSaveable {
        mutableStateOf(
            if (isGranted()) {
                PermissionUiState.GRANTED
            } else {
                PermissionUiState.NOT_REQUESTED
            },
        )
    }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            permissionState =
                when {
                    isGranted -> PermissionUiState.GRANTED

                    context.findActivity()?.let {
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            it,
                            permission,
                        )
                    } == true -> PermissionUiState.DENIED

                    // Android no longer shows the dialog: the user must go to system settings.
                    else -> PermissionUiState.PERMANENTLY_DENIED
                }

            if (isGranted) {
                libraryViewModel.triggerScan()
            }
        }

    LaunchedEffect(Unit) {
        if (permissionState == PermissionUiState.NOT_REQUESTED) {
            launcher.launch(permission)
        }
    }

    // Pick up a grant (or revoke) made in system settings while the app was in the background.
    LifecycleResumeEffect(Unit) {
        val granted = isGranted()

        if (granted && permissionState != PermissionUiState.GRANTED) {
            permissionState = PermissionUiState.GRANTED
            libraryViewModel.triggerScan()
        } else if (!granted && permissionState == PermissionUiState.GRANTED) {
            permissionState = PermissionUiState.DENIED
        }

        onPauseOrDispose {}
    }

    // The section navigation is only shown over actual library content.
    LaunchedEffect(permissionState) { onContentShownChange(permissionState == PermissionUiState.GRANTED) }

    if (permissionState == PermissionUiState.GRANTED) {
        LibraryScreen(
            viewModel = libraryViewModel,
            playlistsViewModel = playlistsViewModel,
            section = section,
            onOpenDrawer = onOpenDrawer,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onOpenPlaylist = onOpenPlaylist,
            isSearchActive = isSearchActive,
            onSearchActiveChange = onSearchActiveChange,
            chromeScroll = chromeScroll,
        )
    } else {
        PermissionRequestView(
            permanentlyDenied = permissionState == PermissionUiState.PERMANENTLY_DENIED,
            onRequestPermission = { launcher.launch(permission) },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}

@Composable
private fun PermissionRequestView(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.xl),
        ) {
            Text(
                text = "Music access needed",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text =
                    if (permanentlyDenied) {
                        "empee3 can't read your music files. Enable the music/audio permission for empee3 in system settings."
                    } else {
                        "empee3 needs permission to read the audio files on this device to build your library."
                    },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            if (permanentlyDenied) {
                Button(onClick = onOpenSettings) {
                    Text("Open settings")
                }
            } else {
                Button(onClick = onRequestPermission) {
                    Text("Grant permission")
                }
            }
        }
    }
}
