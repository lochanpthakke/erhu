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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lochanpthakke.empee3.feature.library.LibraryScreen
import com.lochanpthakke.empee3.feature.library.LibraryViewModel
import com.lochanpthakke.empee3.navigation.Library
import com.lochanpthakke.empee3.navigation.NowPlaying
import com.lochanpthakke.empee3.navigation.Settings

@Composable
fun MainScaffold() {
    val navController = rememberNavController()

    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Library,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<Library> {
                LibraryRoute()
            }
            composable<NowPlaying> {
                NowPlayingPlaceholder()
            }
            composable<Settings> {
                SettingsPlaceholder()
            }
        }
    }
}

@Composable
private fun LibraryRoute() {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = hiltViewModel()

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
                viewModel.triggerScan()
            }
        }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(permissionToRequest)
        }
    }

    if (hasPermission) {
        LibraryScreen(viewModel = viewModel)
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
                text = "Storage Permission Required",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "empee3 needs permission to read audio files " +
                        "from your device storage to build your music library.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
private fun NowPlayingPlaceholder() {
    Text("Now Playing — TODO")
}

@Composable
private fun SettingsPlaceholder() {
    Text("Settings — TODO")
}
