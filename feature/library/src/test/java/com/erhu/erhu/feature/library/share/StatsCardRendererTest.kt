package com.erhu.erhu.feature.library.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.erhu.erhu.core.database.repository.PeriodStats
import com.erhu.erhu.core.database.repository.StatsPeriod
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.model.RankedAlbum
import com.erhu.erhu.core.model.RankedArtist
import com.erhu.erhu.core.model.RankedSong
import com.erhu.erhu.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every recap card (4 types x 3 aspect ratios, plus edge cases) with the real Android
 * canvas. The PNGs land in build/stats-cards for visual review at full resolution.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatsCardRendererTest {
    private val out = File("build/stats-cards").apply { mkdirs() }

    private val labels =
        StatsCardLabels(
            periodLabel = "Last 30 days",
            soundCapsule = "Sound Capsule",
            topSongs = "Top songs",
            topArtists = "Top artists",
            topAlbums = "Top albums",
            minutesListened = "Minutes listened",
            noActivity = "No listening activity",
            noActivityDescription = "No plays recorded for this period",
            playsUnit = { if (it == 1) "play" else "plays" },
            tracksUnit = { if (it == 1) "track" else "tracks" },
            playsCount = { if (it == 1) "1 play" else "$it plays" },
            number = { "%,d".format(it) },
        )

    private fun art(
        hue: Float,
        width: Int = 320,
        height: Int = 320,
    ): Bitmap {
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.85f))
        val d = Color.HSVToColor(floatArrayOf((hue + 40) % 360, 0.8f, 0.35f))
        Canvas(b).drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), c, d, Shader.TileMode.CLAMP) })
        // A centred circle shows at a glance whether an image was cropped or stretched.
        Canvas(b).drawCircle(width / 2f, height / 2f, minOf(width, height) * 0.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return b
    }

    private val titles =
        listOf(
            "BackOutsideBoyz",
            "2 Hard 4 The Radio",
            "A Song With An Extremely Long Title That Will Certainly Not Fit On One Line",
            "SOS",
            "Gone Girl",
        )

    private fun stats(count: Int = 5): PeriodStats =
        PeriodStats(
            period = StatsPeriod.LAST_30_DAYS,
            topSongs = (0 until count).map { RankedSong(Song(it.toLong(), titles[it], if (it == 2) "Kanye West, Jay-Z, Pusha T, Cyhi The Prynce, Swizz Beatz & RZA" else "SZA", "SOS", 1000, "content://s/$it"), 12 - it * 2) },
            topAlbums = (0 until count).map { RankedAlbum(Album(it.toLong(), if (it == 1) "My Beautiful Dark Twisted Fantasy (Deluxe Edition)" else "Album $it", "Kanye West", 1, 10, 2020), 9 - it) },
            topArtists = (0 until count).map { RankedArtist(Artist(it.toLong(), if (it == 4) "Tyler, The Creator" else "Artist $it", 10, 1), 30 - it * 5) },
            totalPlays = 64,
            totalListenedMs = 65L * 60_000L,
            uniqueTracks = 61,
        )

    private fun artwork(count: Int = 5): Map<String, Bitmap> =
        buildMap {
            repeat(count) {
                put(StatsCardRenderer.songKey(it.toLong()), art(it * 60f))
                // A wide cover: it must be cropped, not squashed.
                put(StatsCardRenderer.albumKey(it.toLong()), if (it == 0) art(200f, 480, 240) else art(it * 50f + 20f))
                // Artist 3 has no photo: the monogram fallback.
                if (it != 3) put(StatsCardRenderer.artistKey(it.toLong()), art(it * 70f + 10f, 240, 360))
            }
        }

    @Test
    fun everyTypeAndRatioRendersAtItsExportSize() {
        val stats = stats()
        val artwork = artwork()
        for (type in StatsCardType.entries) {
            for (ratio in CardAspectRatio.entries) {
                val card = StatsCardRenderer.render(stats, type, artwork, StatsShareConfig(ratio, 0.4f), labels)
                assertEquals(ratio.width, card.width)
                assertEquals(ratio.height, card.height)
                save(card, "${type.name.lowercase()}_${ratio.name.lowercase()}")
            }
        }
    }

    @Test
    fun fewerThanFiveResultsAndEmptyPeriodsRender() {
        for (ratio in CardAspectRatio.entries) {
            save(StatsCardRenderer.render(stats(2), StatsCardType.OVERVIEW, artwork(2), StatsShareConfig(ratio), labels), "overview_two_${ratio.name.lowercase()}")
            save(StatsCardRenderer.render(PeriodStats(StatsPeriod.LAST_7_DAYS), StatsCardType.OVERVIEW, emptyMap(), StatsShareConfig(ratio), labels), "empty_${ratio.name.lowercase()}")
        }
        save(StatsCardRenderer.render(stats(2), StatsCardType.TOP_ARTISTS, artwork(2), StatsShareConfig(CardAspectRatio.STORY), labels), "top_artists_two_story")
    }

    @Test
    fun aBrightCoverIsDarkenedEnoughForWhiteText() {
        val white = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val stats = stats(1)
        val card = StatsCardRenderer.render(stats, StatsCardType.TOP_SONGS, mapOf(StatsCardRenderer.songKey(0) to white), StatsShareConfig(CardAspectRatio.SQUARE, darkness = 0.15f), labels)
        // A background-only spot (left margin, below the list): luminance at most 0.183 gives 4.5:1 with white.
        val px = card.getPixel(20, card.height - 20)
        val luminance = (0.2126 * Color.red(px) + 0.7152 * Color.green(px) + 0.0722 * Color.blue(px)) / 255.0
        assertTrue("background luminance $luminance", luminance < 0.5)
        save(card, "bright_cover_square")
    }

    private fun save(
        card: Bitmap,
        name: String,
    ) {
        File(out, "$name.png").outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
