package com.erhu.erhu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.erhu.erhu.core.playback.service.PlaybackServiceConnector
import com.erhu.erhu.perf.FrameDiagnostics
import com.erhu.erhu.ui.theme.ErhuTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var playbackServiceConnector: PlaybackServiceConnector

    @Inject lateinit var announcements: com.erhu.erhu.announcements.AnnouncementController

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        announcements.requestFromNotification(intent.getStringExtra(com.erhu.erhu.announcements.ANNOUNCEMENT_EXTRA))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState ==
            null
        ) {
            announcements.requestFromNotification(intent.getStringExtra(com.erhu.erhu.announcements.ANNOUNCEMENT_EXTRA))
        }
        // Bind the MediaLibraryService up front so the media session (and the
        // notification / lock-screen controls) exist as soon as playback starts.
        playbackServiceConnector.ensureConnected()
        enableEdgeToEdge()
        // Debug builds: slow frames logged with what the app was doing (no-op otherwise).
        FrameDiagnostics.install(this)
        setContent {
            ErhuTheme {
                com.erhu.erhu.onboarding
                    .AppEntry()
            }
        }
    }
}
