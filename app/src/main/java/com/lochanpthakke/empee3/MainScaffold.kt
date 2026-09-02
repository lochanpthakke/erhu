package com.lochanpthakke.empee3

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                LibraryPlaceholder()
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
private fun LibraryPlaceholder() {
    androidx.compose.material3.Text("Library — TODO")
}

@Composable
private fun NowPlayingPlaceholder() {
    androidx.compose.material3.Text("Now Playing — TODO")
}

@Composable
private fun SettingsPlaceholder() {
    androidx.compose.material3.Text("Settings — TODO")
}
