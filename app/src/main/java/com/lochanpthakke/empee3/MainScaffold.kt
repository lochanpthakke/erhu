package com.lochanpthakke.empee3

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.ImageSearch
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Theme
import com.lochanpthakke.empee3.feature.library.ArtworkManagerScreen
import com.lochanpthakke.empee3.feature.library.LibraryScreen
import com.lochanpthakke.empee3.feature.library.LibraryViewModel
import com.lochanpthakke.empee3.feature.library.MetadataEditorScreen
import com.lochanpthakke.empee3.feature.library.NowPlayingScreen
import com.lochanpthakke.empee3.feature.library.PlaylistsScreen
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
import com.lochanpthakke.empee3.navigation.Playlists
import com.lochanpthakke.empee3.navigation.Settings
import com.lochanpthakke.empee3.ui.components.MiniPlayer
import kotlinx.coroutines.launch

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun MainScaffold() {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val soundViewModel: SoundViewModel = hiltViewModel()
    val diagnosticsViewModel: DiagnosticsViewModel = hiltViewModel()
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val playbackRepository = libraryViewModel.playbackRepository

    val currentTheme by themeViewModel.appTheme.collectAsState()
    val currentSkin by themeViewModel.appSkin.collectAsState()
    val isRollbackActive by themeViewModel.isRollbackActive.collectAsState()

    if (isRollbackActive) {
        ThemeRollbackDialog(onDismiss = { themeViewModel.acknowledgeRollback() })
    }

    EmPee3Theme(
        themeManifest = currentTheme,
        skinManifest = currentSkin,
    ) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val openDrawer: () -> Unit = {
            scope.launch { drawerState.open() }
        }

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

                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                        label = { Text("Library") },
                        selected = currentDestination?.route?.contains("Library") == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(Library) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                        label = { Text("Playlists") },
                        selected = currentDestination?.route?.contains("Playlists") == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(Playlists) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Equalizer, contentDescription = null) },
                        label = { Text("Equalizer") },
                        selected = currentDestination?.route?.contains("Equalizer") == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(Equalizer) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                        label = { Text("Metadata Editor") },
                        selected = currentDestination?.route?.contains("MetadataEditor") == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(MetadataEditor) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.ImageSearch, contentDescription = null) },
                        label = { Text("Artwork Manager") },
                        selected = currentDestination?.route?.contains("ArtworkManager") == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(ArtworkManager) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                        selected = currentDestination?.route?.contains("Settings") == true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(Settings()) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            },
        ) {
            Scaffold(
                bottomBar = {
                    if (currentDestination?.route?.contains("NowPlaying") != true) {
                        MiniPlayer(
                            playbackRepository = playbackRepository,
                            onMiniPlayerClick = {
                                navController.navigate(NowPlaying)
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
                            onOpenDrawer = openDrawer,
                        )
                    }
                    composable<NowPlaying> {
                        NowPlayingScreen(
                            playbackRepository = playbackRepository,
                            onBackClick = { navController.popBackStack() },
                        )
                    }
                    composable<Playlists> {
                        PlaylistsScreen(onOpenDrawer = openDrawer)
                    }
                    composable<Equalizer> {
                        SoundSettingsScreen(viewModel = soundViewModel)
                    }
                    composable<MetadataEditor> {
                        MetadataEditorScreen(
                            onOpenDrawer = openDrawer,
                            onNavigateToMetadataSettings = {
                                navController.navigate(Settings(category = "metadata"))
                            },
                        )
                    }
                    composable<ArtworkManager> {
                        ArtworkManagerScreen(
                            onOpenDrawer = openDrawer,
                            onNavigateToMetadataSettings = {
                                navController.navigate(Settings(category = "metadata"))
                            },
                        )
                    }
                    composable<Settings> {
                        SettingsScreen(
                            onOpenDrawer = openDrawer,
                            onNavigateToMetadataEditor = {
                                navController.navigate(MetadataEditor)
                            },
                            onNavigateToArtworkManager = {
                                navController.navigate(ArtworkManager)
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
}

@Composable
private fun LibraryRoute(
    libraryViewModel: LibraryViewModel,
    onOpenDrawer: () -> Unit,
) {
    val context = LocalContext.current

    val permissionToRequest =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                permissionToRequest,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                libraryViewModel.triggerScan()
            }
        }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(permissionToRequest)
        }
    }

    if (hasPermission) {
        LibraryScreen(
            viewModel = libraryViewModel,
            onOpenDrawer = onOpenDrawer,
        )
    } else {
        PermissionRequestView(onRequestPermission = { launcher.launch(permissionToRequest) })
    }
}

@Composable
private fun PermissionRequestView(
    onRequestPermission: () -> Unit,
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
                text = "Storage Permission Denied",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "Storage Permission Denied: Not agreeing to this permission means empee3 cannot scan or " +
                        "access any local music files on your device. Audio playback will not work until storage access is granted.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}
