package com.lochanpthakke.empee3

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Theme
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.feature.library.AddToPlaylistDialog
import com.lochanpthakke.empee3.feature.library.ArtworkManagerScreen
import com.lochanpthakke.empee3.feature.library.LibraryScreen
import com.lochanpthakke.empee3.feature.library.LibraryViewModel
import com.lochanpthakke.empee3.feature.library.MetadataEditorScreen
import com.lochanpthakke.empee3.feature.library.NowPlayingScreen
import com.lochanpthakke.empee3.feature.library.PlaylistDetailScreen
import com.lochanpthakke.empee3.feature.library.PlaylistsScreen
import com.lochanpthakke.empee3.feature.library.PlaylistsViewModel
import com.lochanpthakke.empee3.feature.settings.SettingsScreen
import com.lochanpthakke.empee3.feature.settings.diagnostics.DiagnosticsViewModel
import com.lochanpthakke.empee3.feature.settings.sound.SoundSettingsScreen
import com.lochanpthakke.empee3.feature.settings.sound.SoundViewModel
import com.lochanpthakke.empee3.feature.settings.theme.ThemeRollbackDialog
import com.lochanpthakke.empee3.feature.settings.theme.ThemeViewModel
import com.lochanpthakke.empee3.navigation.ArtworkManager
import com.lochanpthakke.empee3.navigation.Equalizer
import com.lochanpthakke.empee3.navigation.Library
import com.lochanpthakke.empee3.navigation.MetadataEditor
import com.lochanpthakke.empee3.navigation.NowPlaying
import com.lochanpthakke.empee3.navigation.PlaylistDetail
import com.lochanpthakke.empee3.navigation.Playlists
import com.lochanpthakke.empee3.navigation.Settings
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
        DrawerDestination("Playlists", Icons.AutoMirrored.Filled.QueueMusic, Playlists),
        DrawerDestination("Equalizer", Icons.Default.Equalizer, Equalizer),
        DrawerDestination("Metadata Editor", Icons.Default.EditNote, MetadataEditor),
        DrawerDestination("Settings", Icons.Default.Settings, Settings()),
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

    val currentTheme by themeViewModel.appTheme.collectAsState()
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
    ) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val isOnNowPlaying = currentDestination?.hasRoute(NowPlaying::class) == true

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

        var songForPlaylist by remember { mutableStateOf<Song?>(null) }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                    ) {
                        Text(
                            text = "empee3",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Local Music Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    DRAWER_DESTINATIONS.forEach { destination ->
                        NavigationDrawerItem(
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                            selected = currentDestination?.hasRoute(destination.route::class) == true,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigateTopLevel(destination.route)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            },
        ) {
            Scaffold(
                bottomBar = {
                    if (!isOnNowPlaying) {
                        MiniPlayer(
                            playbackRepository = playbackRepository,
                            onMiniPlayerClick = {
                                navController.navigate(NowPlaying) { launchSingleTop = true }
                            },
                        )
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Library,
                    modifier = Modifier.padding(innerPadding),
                ) {
                    composable<Library> {
                        LibraryRoute(
                            libraryViewModel = libraryViewModel,
                            playlistsViewModel = playlistsViewModel,
                            onOpenDrawer = openDrawer,
                        )
                    }

                    composable<NowPlaying> {
                        val isFavorite by libraryViewModel.currentSongIsFavorite.collectAsState()
                        val lyrics by libraryViewModel.currentSongLyrics.collectAsState()

                        NowPlayingScreen(
                            playbackRepository = playbackRepository,
                            isFavorite = isFavorite,
                            lyrics = lyrics,
                            onBackClick = { navController.popBackStack() },
                            onToggleFavorite = { libraryViewModel.toggleFavorite(it) },
                            onAddToPlaylist = { songForPlaylist = it },
                        )
                    }

                    composable<Playlists> {
                        PlaylistsScreen(
                            viewModel = playlistsViewModel,
                            onOpenDrawer = openDrawer,
                            onOpenPlaylist = { navController.navigate(PlaylistDetail(it)) },
                        )
                    }

                    composable<PlaylistDetail> { entry ->
                        val route = entry.toRoute<PlaylistDetail>()
                        val playbackState by playbackRepository.playbackState.collectAsState()

                        PlaylistDetailScreen(
                            playlistId = route.playlistId,
                            viewModel = playlistsViewModel,
                            currentSongId = playbackState.currentSong?.id,
                            onBackClick = { navController.popBackStack() },
                        )
                    }

                    composable<Equalizer> {
                        SoundSettingsScreen(viewModel = soundViewModel)
                    }

                    composable<MetadataEditor> {
                        MetadataEditorScreen(
                            viewModel = libraryViewModel,
                            onOpenDrawer = openDrawer,
                            onNavigateToMetadataSettings = {
                                navController.navigate(Settings(category = "metadata"))
                            },
                            onNavigateToLibrary = {
                                navController.navigateTopLevel(Library)
                            },
                        )
                    }

                    composable<ArtworkManager> {
                        ArtworkManagerScreen(
                            viewModel = libraryViewModel,
                            onOpenDrawer = openDrawer,
                            onNavigateToMetadataSettings = {
                                navController.navigate(Settings(category = "metadata"))
                            },
                            onNavigateToLibrary = {
                                navController.navigate(Library)
                            },
                        )
                    }

                    composable<Settings> { entry ->
                        val route = entry.toRoute<Settings>()

                        SettingsScreen(
                            onOpenDrawer = openDrawer,
                            onNavigateToMetadataEditor = {
                                navController.navigate(MetadataEditor)
                            },
                            themeViewModel = themeViewModel,
                            soundViewModel = soundViewModel,
                            diagnosticsViewModel = diagnosticsViewModel,
                            initialCategory = route.category,
                        )
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
    onOpenDrawer: () -> Unit,
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

    if (permissionState == PermissionUiState.GRANTED) {
        LibraryScreen(
            viewModel = libraryViewModel,
            playlistsViewModel = playlistsViewModel,
            onOpenDrawer = onOpenDrawer,
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
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Music access needed",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

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
