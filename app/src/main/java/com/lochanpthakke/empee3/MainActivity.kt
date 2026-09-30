package com.lochanpthakke.empee3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lochanpthakke.empee3.core.playback.service.PlaybackServiceConnector
import com.lochanpthakke.empee3.ui.theme.Empee3Theme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var playbackServiceConnector: PlaybackServiceConnector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bind the MediaLibraryService up front so the media session (and the
        // notification / lock-screen controls) exist as soon as playback starts.
        playbackServiceConnector.ensureConnected()
        enableEdgeToEdge()
        setContent {
            Empee3Theme {
                MainScaffold()
            }
        }
    }
}
