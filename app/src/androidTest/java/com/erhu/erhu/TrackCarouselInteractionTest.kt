@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.erhu.erhu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.theme.BuiltInThemes
import com.erhu.erhu.core.designsystem.theme.ErhuTheme
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.feature.library.CarouselCard
import com.erhu.erhu.feature.library.TrackCarousel
import com.erhu.erhu.feature.library.TrackCarouselState
import com.erhu.erhu.feature.library.TrackChangeEffect
import com.erhu.erhu.feature.library.rememberTrackCarouselState
import com.erhu.erhu.feature.library.trackCarouselSwipe
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real pointer dispatch and card compositions in the isolated .bench application. */
class TrackCarouselInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var index by mutableIntStateOf(2)
    private val commands = mutableListOf<Int>()
    private lateinit var carousel: TrackCarouselState
    // Repeated entries and absent covers exercise occurrence identity and the real fallback art.
    private lateinit var songs: List<Song>

    private fun show() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val covers = listOf(600 to 200, 200 to 600).mapIndexed { i, (width, height) ->
            val file = java.io.File(context.cacheDir, "carousel-test-$i.png")
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(if (i == 0) android.graphics.Color.RED else android.graphics.Color.BLUE)
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            android.net.Uri.fromFile(file).toString()
        }
        songs = List(7) {
            val id = listOf(0, 1, 1, 1, 2, 0, 2)[it]
            Song(id.toLong(), "Track $id", "Artist", "Album", 180000L,
                "file:///carousel-fixture-$id.mp3", artworkUri = covers.getOrNull(id))
        }
        compose.setContent {
            ErhuTheme(themeManifest = BuiltInThemes.DEFAULT_DARK) {
                carousel = rememberTrackCarouselState()
                val previous = songs.getOrNull(index - 1)
                val next = songs.getOrNull(index + 1)
                TrackChangeEffect(carousel, songs[index], index, previous, next)
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TrackCarousel(
                        carousel, previous, songs[index], next,
                        Modifier.height(440.dp).testTag("carousel").trackCarouselSwipe(
                            carousel, { index < songs.lastIndex }, { index > 0 },
                        ) { direction -> commands += direction; index += direction },
                    ) { song, current, distance, slot ->
                        CarouselCard(song, current, distance, null, false, {}, {
                            commands += slot
                            index += slot
                        })
                    }
                    Text("${index + 1} / ${songs.size}", color = Color.White)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun nextPreviousAndRapidSwipesDispatchOnceAndSettleOnThePlaybackEntry() {
        show()
        compose.onNodeWithTag("carousel").performTouchInput { swipeLeft(durationMillis = 350) }
        compose.runOnIdle { assertEquals(listOf(1), commands); assertEquals(3, index); assertEquals(0f, carousel.position, 0.1f) }
        compose.onNodeWithTag("carousel").performTouchInput { swipeRight(durationMillis = 350) }
        compose.runOnIdle { assertEquals(listOf(1, -1), commands); assertEquals(2, index) }
        repeat(3) { compose.onNodeWithTag("carousel").performTouchInput { swipeLeft(durationMillis = 100) } }
        compose.runOnIdle { assertEquals(5, index); assertEquals(5, commands.size); assertEquals(0f, carousel.position, 0.1f) }
    }

    @Test
    fun externalChangesAndRepeatedOccurrencesRetainTheIncomingCard() {
        show()
        compose.runOnIdle { index++ }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3, index); assertEquals(0, commands.size); assertEquals(0f, carousel.position, 0.1f) }
        repeat(4) {
            compose.onNodeWithTag("carousel").performTouchInput { swipeLeft(durationMillis = 250) }
            compose.onNodeWithTag("carousel").performTouchInput { swipeRight(durationMillis = 250) }
        }
        compose.runOnIdle { assertEquals(3, index); assertEquals(8, commands.size); assertTrue(carousel.page > 0f) }
    }
}
