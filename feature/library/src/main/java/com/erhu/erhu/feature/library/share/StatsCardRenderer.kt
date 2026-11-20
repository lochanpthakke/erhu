package com.erhu.erhu.feature.library.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import com.erhu.erhu.core.database.repository.PeriodStats
import kotlin.math.max
import kotlin.math.min

/** Everything a recap card says, resolved in the app's language before rendering. */
data class StatsCardLabels(
    val periodLabel: String,
    val soundCapsule: String,
    val topSongs: String,
    val topArtists: String,
    val topAlbums: String,
    val minutesListened: String,
    val noActivity: String,
    val noActivityDescription: String,
    /** "Plays" / "Tracks" for a count, as a unit label. */
    val playsUnit: (Int) -> String,
    val tracksUnit: (Int) -> String,
    /** "2 plays" for one ranked item. */
    val playsCount: (Int) -> String,
    val number: (Long) -> String,
)

/**
 * The Listening Recap image (1080 px wide) for every card type and aspect ratio.
 *
 * One layout engine: every size is a base size times a scale chosen so the content fills the
 * card's own height, so a Square card is composed for a square instead of being a shrunken
 * Story. The structure changes with the room available: Story stacks one column, Portrait
 * and Square put Top Songs and Top Albums side by side, and Square also moves the totals
 * next to the title. The preview shows this same bitmap, so a saved or shared card is
 * exactly the preview.
 */
object StatsCardRenderer {
    fun render(
        stats: PeriodStats,
        type: StatsCardType,
        artwork: Map<String, Bitmap>,
        config: StatsShareConfig,
        labels: StatsCardLabels,
    ): Bitmap {
        val ratio = config.aspectRatio
        val output = Bitmap.createBitmap(ratio.width, ratio.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val background =
            stats.topSongs.firstOrNull()?.let { artwork[songKey(it.song.id)] }
                ?: stats.topAlbums.firstOrNull()?.let { artwork[albumKey(it.album.id)] }
                ?: stats.topArtists.firstOrNull()?.let { artwork[artistKey(it.artist.id)] }
        ShareCardRenderer.drawBackground(
            canvas,
            background,
            ratio.width,
            ratio.height,
            BlurIntensity.DEEP,
            config.darkness,
            keepTextReadable = true,
        )

        val frame = Frame.of(ratio)
        val card = Card(canvas, frame, artwork, labels)
        val empty =
            when (type) {
                StatsCardType.OVERVIEW -> stats.isEmpty
                StatsCardType.TOP_SONGS -> stats.topSongs.isEmpty()
                StatsCardType.TOP_ARTISTS -> stats.topArtists.isEmpty()
                StatsCardType.TOP_ALBUMS -> stats.topAlbums.isEmpty()
            }
        when {
            empty -> card.empty(eyebrow = eyebrow(type, labels))
            type == StatsCardType.OVERVIEW -> card.overview(stats, ratio)
            else -> card.ranking(stats, type, eyebrow(type, labels))
        }
        ShareCardRenderer.drawBranding(canvas, ratio.width, ratio.height, frame.brandBaseline)
        return output
    }

    fun songKey(id: Long) = "song_$id"

    fun albumKey(id: Long) = "album_$id"

    fun artistKey(id: Long) = "artist_$id"

    private fun eyebrow(
        type: StatsCardType,
        labels: StatsCardLabels,
    ): String =
        when (type) {
            StatsCardType.TOP_SONGS -> labels.topSongs
            StatsCardType.TOP_ARTISTS -> labels.topArtists
            StatsCardType.TOP_ALBUMS -> labels.topAlbums
            StatsCardType.OVERVIEW -> labels.soundCapsule
        }

    /** Margins of one aspect ratio, in px of the 1080 px wide card. */
    private class Frame(
        val width: Float,
        val top: Float,
        /** Lowest point content may reach; the branding sits below it. */
        val bottom: Float,
        val brandBaseline: Float,
    ) {
        val left = MARGIN
        val right = width - MARGIN
        val contentWidth = right - left

        companion object {
            fun of(ratio: CardAspectRatio): Frame {
                val w = ratio.width.toFloat()
                val h = ratio.height.toFloat()
                // Stories keep clear of the viewer's own top and bottom bars.
                val (top, bottomMargin) =
                    when (ratio) {
                        CardAspectRatio.STORY -> 168f to 150f
                        CardAspectRatio.FEED -> 96f to 92f
                        CardAspectRatio.SQUARE -> 76f to 80f
                    }
                val brandBaseline = h - bottomMargin
                return Frame(w, top, brandBaseline - BRAND_CLEARANCE, brandBaseline)
            }
        }
    }

    private class Card(
        val canvas: Canvas,
        val frame: Frame,
        val artwork: Map<String, Bitmap>,
        val labels: StatsCardLabels,
    ) {
        private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private val regular = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        private fun paint(
            size: Float,
            typeface: Typeface,
            alpha: Int = 255,
            spacing: Float = 0f,
            align: Paint.Align = Paint.Align.LEFT,
        ) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.typeface = typeface
            color = Color.argb(alpha, 255, 255, 255)
            letterSpacing = spacing
            textAlign = align
        }

        /** Line height actually used by [p]'s font, so stacked lines never touch. */
        private fun lineHeight(p: Paint) = p.fontMetrics.let { it.descent - it.ascent }

        /** Draws [text] with its line box starting at [top]; returns the line box height. */
        private fun text(
            text: String,
            x: Float,
            top: Float,
            p: TextPaint,
            maxWidth: Float = Float.MAX_VALUE,
        ): Float {
            canvas.drawText(ShareCardRenderer.ellipsize(text, p, maxWidth), x, top - p.fontMetrics.ascent, p)
            return lineHeight(p)
        }

        /** [text] vertically centred on [centerY]. */
        private fun centredText(
            text: String,
            x: Float,
            centerY: Float,
            p: TextPaint,
            maxWidth: Float = Float.MAX_VALUE,
        ) {
            val m = p.fontMetrics
            canvas.drawText(ShareCardRenderer.ellipsize(text, p, maxWidth), x, centerY - (m.ascent + m.descent) / 2f, p)
        }

        private fun eyebrowPaint(k: Float) = paint(EYEBROW * k, bold, alpha = 190, spacing = 0.16f)

        private fun titlePaint(k: Float) = paint(TITLE * k, bold, spacing = 0.01f)

        private fun headingPaint(k: Float) = paint(HEADING * k, bold, alpha = 200, spacing = 0.14f)

        private fun headerHeight(k: Float) = lineHeight(eyebrowPaint(k)) + EYEBROW_GAP * k + lineHeight(titlePaint(k))

        private fun header(
            eyebrow: String,
            top: Float,
            k: Float,
            maxWidth: Float = frame.contentWidth,
        ): Float {
            var y = top
            y += text(eyebrow.uppercase(), frame.left, y, eyebrowPaint(k), maxWidth)
            y += EYEBROW_GAP * k
            y += text(labels.periodLabel, frame.left, y, titlePaint(k), maxWidth)
            return y - top
        }

        private fun headingHeight(k: Float) = lineHeight(headingPaint(k)) + HEADING_GAP * k

        private fun heading(
            title: String,
            x: Float,
            top: Float,
            k: Float,
            maxWidth: Float,
        ): Float {
            text(title.uppercase(), x, top, headingPaint(k), maxWidth)
            return headingHeight(k)
        }

        fun empty(eyebrow: String) {
            val k = 1f
            header(eyebrow, frame.top, k)
            val cx = frame.width / 2f
            val cy = (frame.top + frame.bottom) / 2f
            canvas.drawCircle(cx, cy - 70f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 255, 255) })
            centredText("♪", cx, cy - 70f, paint(56f, bold, alpha = 200, align = Paint.Align.CENTER))
            text(labels.noActivity, cx, cy + 28f, paint(40f, bold, align = Paint.Align.CENTER), frame.contentWidth)
            text(
                labels.noActivityDescription,
                cx,
                cy + 88f,
                paint(30f, regular, alpha = 170, align = Paint.Align.CENTER),
                frame.contentWidth,
            )
        }

        // ---- Single rankings: Top Songs / Top Artists / Top Albums ----

        fun ranking(
            stats: PeriodStats,
            type: StatsCardType,
            eyebrow: String,
        ) {
            val rows: List<Row> =
                when (type) {
                    StatsCardType.TOP_SONGS ->
                        stats.topSongs
                            .take(
                                TOP,
                            ).map { Row(it.song.title, it.song.artist, it.plays, artwork[songKey(it.song.id)], round = false) }
                    StatsCardType.TOP_ALBUMS ->
                        stats.topAlbums
                            .take(
                                TOP,
                            ).map { Row(it.album.title, it.album.artist, it.plays, artwork[albumKey(it.album.id)], round = false) }
                    else ->
                        stats.topArtists
                            .take(
                                TOP,
                            ).map { Row(it.artist.name, null, it.plays, artwork[artistKey(it.artist.id)], round = true) }
                }

            // Rows share the height evenly (five slots, so fewer results keep the same size), and
            // the header follows the row size so the two keep their proportion on every card.
            fun rowHeightFor(k: Float) =
                min(
                    (frame.bottom - frame.top - headerHeight(k) - SECTION_GAP * k) / TOP,
                    frame.contentWidth * MAX_ROW_FRACTION,
                )
            val k = (rowHeightFor(1f) / HEADER_ROW_REFERENCE).coerceIn(MIN_SCALE, MAX_SCALE)
            val head = headerHeight(k) + SECTION_GAP * k
            val rowHeight = rowHeightFor(k)
            val block = head + rowHeight * TOP
            // Centre the whole block between the top margin and the branding.
            var y = frame.top + max(0f, (frame.bottom - frame.top - block) / 2f)
            y += header(eyebrow, y, k) + SECTION_GAP * k
            rows.forEachIndexed { index, row ->
                rankedRow(index + 1, row, frame.left, y, frame.contentWidth, rowHeight, showRank = true)
                y += rowHeight
            }
        }

        private class Row(
            val title: String,
            val subtitle: String?,
            val plays: Int,
            val art: Bitmap?,
            val round: Boolean,
        )

        /**
         * One ranked entry: rank, artwork, title over subtitle, play count at the end. Every size
         * follows [height], so the same row works in a full-width list and in a narrow column.
         */
        private fun rankedRow(
            rank: Int,
            row: Row,
            x: Float,
            top: Float,
            width: Float,
            height: Float,
            showRank: Boolean,
        ) {
            val centerY = top + height / 2f
            val thumb = height * THUMB_FRACTION
            val titleSize = (height * TITLE_FRACTION).coerceIn(MIN_ROW_TITLE, MAX_ROW_TITLE)
            val titleP = paint(titleSize, bold)
            val subP = paint(titleSize * SUBTITLE_RATIO, regular, alpha = 185)
            val countP = paint(titleSize * SUBTITLE_RATIO, medium, alpha = 170, align = Paint.Align.RIGHT)

            var cursor = x
            if (showRank) {
                val rankP = paint(titleSize * RANK_RATIO, bold, alpha = 205, align = Paint.Align.CENTER)
                val rankWidth = rankP.measureText("5") + titleSize * 0.5f
                centredText(labels.number(rank.toLong()), cursor + rankWidth / 2f, centerY, rankP)
                cursor += rankWidth + titleSize * 0.35f
            }
            val dst = android.graphics.RectF(cursor, centerY - thumb / 2f, cursor + thumb, centerY + thumb / 2f)
            artworkAt(row.art, dst, row.round, row.title)
            cursor += thumb + height * TEXT_GAP_FRACTION

            val count = labels.playsCount(row.plays)
            val countWidth = countP.measureText(count)
            val fullWidth = x + width - cursor
            val besideCount = fullWidth - countWidth - titleSize * 0.6f
            val subtitle = row.subtitle
            if (subtitle == null) {
                centredText(row.title, cursor, centerY, titleP, besideCount)
                centredText(count, x + width, centerY, countP)
                return
            }
            val gap = titleSize * LINE_GAP_RATIO
            val block = lineHeight(titleP) + gap + lineHeight(subP)
            val textTop = centerY - block / 2f
            val subtitleTop = textTop + lineHeight(titleP) + gap
            // In a narrow column the count moves onto the subtitle line, so the title keeps the
            // whole width; in a full-width list it sits at the end, centred on the row.
            val narrow = width < frame.contentWidth * NARROW_ROW_FRACTION
            text(row.title, cursor, textTop, titleP, if (narrow) fullWidth else besideCount)
            text(subtitle, cursor, subtitleTop, subP, besideCount)
            if (narrow) {
                text(count, x + width, subtitleTop, countP)
            } else {
                centredText(count, x + width, centerY, countP)
            }
        }

        private fun artworkAt(
            art: Bitmap?,
            dst: android.graphics.RectF,
            round: Boolean,
            name: String,
        ) {
            when {
                art != null && round -> ShareCardRenderer.drawCircularBitmap(canvas, art, dst)
                art != null -> ShareCardRenderer.drawRoundedBitmap(canvas, art, dst, dst.width() * ART_RADIUS)
                round -> ShareCardRenderer.drawMonogramAvatar(canvas, name, dst)
                else -> ShareCardRenderer.drawPlaceholderArtwork(canvas, dst, dst.width() * ART_RADIUS)
            }
        }

        // ---- Overview ----

        fun overview(
            stats: PeriodStats,
            ratio: CardAspectRatio,
        ) {
            val songs = stats.topSongs.take(TOP)
            val albums = stats.topAlbums.take(TOP)
            val artists = stats.topArtists.take(TOP)
            val sideBySide = ratio != CardAspectRatio.STORY
            val totalsBesideTitle = ratio == CardAspectRatio.SQUARE
            val gapBase = if (ratio == CardAspectRatio.STORY) SECTION_GAP else COMPACT_SECTION_GAP

            val totalsWidth = frame.contentWidth * SQUARE_TOTALS_FRACTION
            // Album tiles in the Story column are as wide as the card allows, independent of the scale.
            val tile = (frame.contentWidth - TILE_GAP * (TOP - 1)) / TOP

            fun sections(k: Float): List<Float> =
                buildList {
                    add(if (totalsBesideTitle) max(headerHeight(k), totalsHeight(k, totalsWidth, stats)) else headerHeight(k))
                    if (!totalsBesideTitle) add(totalsHeight(k, frame.contentWidth, stats))
                    if (artists.isNotEmpty()) add(artistsHeight(k))
                    if (sideBySide) {
                        if (songs.isNotEmpty() || albums.isNotEmpty()) add(headingHeight(k) + COLUMN_ROW * k * TOP)
                    } else {
                        if (songs.isNotEmpty()) add(headingHeight(k) + STORY_ROW * k * TOP)
                        if (albums.isNotEmpty()) add(headingHeight(k) + albumTileHeight(k, tile))
                    }
                }

            // Heights grow linearly with the scale (album artwork excepted): solve for the scale
            // that fills the space, within readable bounds.
            val available = frame.bottom - frame.top

            fun total(k: Float) = sections(k).sum() + gapBase * k * (sections(k).size - 1)
            val fixed = total(0f)
            val k = ((available - fixed) / (total(1f) - fixed)).coerceIn(MIN_SCALE, MAX_SCALE)
            val heights = sections(k)
            val gaps = heights.size - 1
            // Space left at the largest scale goes between sections first, then centres the block.
            val spare = available - total(k)
            val extraGap = if (gaps > 0) min(max(0f, spare) / gaps, gapBase * k * EXTRA_GAP_LIMIT) else 0f
            val gap = gapBase * k + extraGap
            var y = frame.top + max(0f, (spare - extraGap * gaps) / 2f)

            if (totalsBesideTitle) {
                val headerWidth = frame.contentWidth - totalsWidth - TOTALS_GAP
                val band = heights[0]
                header(labels.soundCapsule, y + (band - headerHeight(k)) / 2f, k, headerWidth)
                totals(stats, frame.right - totalsWidth, y + (band - totalsHeight(k, totalsWidth, stats)) / 2f, totalsWidth, k)
                y += band + gap
            } else {
                y += header(labels.soundCapsule, y, k) + gap
                totals(stats, frame.left, y, frame.contentWidth, k)
                y += totalsHeight(k, frame.contentWidth, stats) + gap
            }
            if (artists.isNotEmpty()) {
                artistsRow(artists.map { Triple(it.artist.name, artwork[artistKey(it.artist.id)], it.artist.id) }, y, k)
                y += artistsHeight(k) + gap
            }
            if (sideBySide) {
                val columnWidth = (frame.contentWidth - COLUMN_GAP) / 2f
                val rowHeight = COLUMN_ROW * k
                if (songs.isNotEmpty()) {
                    var rowTop = y + heading(labels.topSongs, frame.left, y, k, columnWidth)
                    songs.forEachIndexed { i, s ->
                        rankedRow(
                            i + 1,
                            Row(s.song.title, s.song.artist, s.plays, artwork[songKey(s.song.id)], false),
                            frame.left,
                            rowTop,
                            columnWidth,
                            rowHeight,
                            true,
                        )
                        rowTop += rowHeight
                    }
                }
                if (albums.isNotEmpty()) {
                    val x = frame.left + columnWidth + COLUMN_GAP
                    var rowTop = y + heading(labels.topAlbums, x, y, k, columnWidth)
                    albums.forEachIndexed { i, a ->
                        rankedRow(
                            i + 1,
                            Row(a.album.title, a.album.artist, a.plays, artwork[albumKey(a.album.id)], false),
                            x,
                            rowTop,
                            columnWidth,
                            rowHeight,
                            true,
                        )
                        rowTop += rowHeight
                    }
                }
            } else {
                if (songs.isNotEmpty()) {
                    var rowTop = y + heading(labels.topSongs, frame.left, y, k, frame.contentWidth)
                    val rowHeight = STORY_ROW * k
                    songs.forEachIndexed { i, s ->
                        rankedRow(
                            i + 1,
                            Row(s.song.title, s.song.artist, s.plays, artwork[songKey(s.song.id)], false),
                            frame.left,
                            rowTop,
                            frame.contentWidth,
                            rowHeight,
                            true,
                        )
                        rowTop += rowHeight
                    }
                    y = rowTop + gap
                }
                if (albums.isNotEmpty()) {
                    val tilesTop = y + heading(labels.topAlbums, frame.left, y, k, frame.contentWidth)
                    albums.forEachIndexed { i, a ->
                        albumTile(
                            i + 1,
                            a.album.title,
                            a.album.artist,
                            a.plays,
                            artwork[albumKey(a.album.id)],
                            frame.left + i * (tile + TILE_GAP),
                            tilesTop,
                            tile,
                            k,
                        )
                    }
                }
            }
        }

        private fun totalsNumberPaint(k: Float) = paint(TOTAL_NUMBER * k, bold)

        private fun totalsLabelPaint(k: Float) = paint(TOTAL_LABEL * k, bold, alpha = 175, spacing = 0.1f)

        private fun totalsHeight(
            k: Float,
            width: Float,
            stats: PeriodStats,
        ): Float {
            val lines =
                totalsItems(stats).maxOf { (_, label) ->
                    wrap(
                        label.uppercase(),
                        totalsLabelPaint(k),
                        width / TOTALS - TOTAL_COLUMN_GAP,
                    ).size
                }
            return lineHeight(totalsNumberPaint(k)) + TOTAL_GAP * k + lineHeight(totalsLabelPaint(k)) * lines
        }

        private fun totalsItems(stats: PeriodStats) =
            listOf(
                labels.number(stats.totalListenedMs / MS_PER_MINUTE) to labels.minutesListened,
                labels.number(stats.totalPlays.toLong()) to labels.playsUnit(stats.totalPlays),
                labels.number(stats.uniqueTracks.toLong()) to labels.tracksUnit(stats.uniqueTracks),
            )

        /** [text] broken at spaces into at most two lines of [maxWidth]; the second is ellipsized. */
        private fun wrap(
            text: String,
            p: TextPaint,
            maxWidth: Float,
        ): List<String> {
            if (p.measureText(text) <= maxWidth) return listOf(text)
            val words = text.split(' ')
            var first = words.first()
            var i = 1
            while (i < words.size && p.measureText("$first ${words[i]}") <= maxWidth) {
                first += " ${words[i]}"
                i++
            }
            val rest = words.drop(i).joinToString(" ")
            return if (rest.isEmpty()) listOf(first) else listOf(first, rest)
        }

        /** Minutes listened, plays and tracks in three equal columns (the Sound Capsule screen's figures). */
        private fun totals(
            stats: PeriodStats,
            x: Float,
            top: Float,
            width: Float,
            k: Float,
        ) {
            val items = totalsItems(stats)
            val column = width / items.size
            val numberP = totalsNumberPaint(k)
            val labelP = totalsLabelPaint(k)
            items.forEachIndexed { i, (value, label) ->
                val cx = x + column * i
                val maxWidth = column - TOTAL_COLUMN_GAP
                var y = top + text(value, cx, top, numberP, maxWidth) + TOTAL_GAP * k
                wrap(label.uppercase(), labelP, maxWidth).forEach { line -> y += text(line, cx, y, labelP, maxWidth) }
            }
        }

        private fun avatarSize(k: Float) = min(AVATAR * k, frame.contentWidth / TOP - AVATAR_MIN_GAP)

        private fun artistNamePaint(k: Float) = paint(ARTIST_NAME * k, medium, align = Paint.Align.CENTER)

        private fun artistsHeight(k: Float) = headingHeight(k) + avatarSize(k) + AVATAR_NAME_GAP * k + lineHeight(artistNamePaint(k))

        /** Up to five artists in equal slots: circular photo with its rank, name underneath. */
        private fun artistsRow(
            artists: List<Triple<String, Bitmap?, Long>>,
            top: Float,
            k: Float,
        ) {
            var y = top + heading(labels.topArtists, frame.left, top, k, frame.contentWidth)
            val slot = frame.contentWidth / TOP
            val avatar = avatarSize(k)
            val nameP = artistNamePaint(k)
            artists.forEachIndexed { i, (name, art, _) ->
                val cx = frame.left + slot * i + slot / 2f
                val dst = android.graphics.RectF(cx - avatar / 2f, y, cx + avatar / 2f, y + avatar)
                artworkAt(art, dst, round = true, name = name)
                rankBadge(i + 1, dst.left, dst.top, avatar * BADGE_FRACTION)
                text(name, cx, y + avatar + AVATAR_NAME_GAP * k, nameP, slot - AVATAR_NAME_INSET)
            }
            y += avatar
        }

        private fun albumTilePaints(k: Float) =
            Triple(
                paint(TILE_TITLE * k, bold),
                paint(TILE_SUBTITLE * k, regular, alpha = 185),
                paint(TILE_SUBTITLE * k, medium, alpha = 165),
            )

        private fun albumTileHeight(
            k: Float,
            tile: Float,
        ): Float {
            val (t, s, c) = albumTilePaints(k)
            return tile + TILE_TEXT_GAP * k + lineHeight(t) + lineHeight(s) + lineHeight(c)
        }

        private fun albumTile(
            rank: Int,
            title: String,
            artist: String,
            plays: Int,
            art: Bitmap?,
            x: Float,
            top: Float,
            tile: Float,
            k: Float,
        ) {
            val dst = android.graphics.RectF(x, top, x + tile, top + tile)
            artworkAt(art, dst, round = false, name = title)
            rankBadge(rank, x, top, tile * BADGE_FRACTION * 0.8f)
            val (t, s, c) = albumTilePaints(k)
            var y = top + tile + TILE_TEXT_GAP * k
            y += text(title, x, y, t, tile)
            y += text(artist, x, y, s, tile)
            text(labels.playsCount(plays), x, y, c, tile)
        }

        /** A rank number in a small dark circle over the top-left corner of [x], [y]. */
        private fun rankBadge(
            rank: Int,
            x: Float,
            y: Float,
            size: Float,
        ) {
            val inset = size * 0.12f
            val cx = x + inset + size / 2f
            val cy = y + inset + size / 2f
            canvas.drawCircle(cx, cy, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 0, 0, 0) })
            centredText(labels.number(rank.toLong()), cx, cy, paint(size * 0.52f, bold, align = Paint.Align.CENTER))
        }
    }

    private const val TOP = 5
    private const val MARGIN = 80f
    private const val BRAND_CLEARANCE = 56f
    private const val MS_PER_MINUTE = 60_000L

    // Base sizes in px at scale 1 (a 1080 px wide card).
    private const val EYEBROW = 28f
    private const val EYEBROW_GAP = 10f
    private const val TITLE = 64f
    private const val HEADING = 26f
    private const val HEADING_GAP = 22f
    private const val SECTION_GAP = 56f
    private const val COMPACT_SECTION_GAP = 40f
    private const val EXTRA_GAP_LIMIT = 0.8f
    private const val MIN_SCALE = 0.82f
    private const val MAX_SCALE = 1.15f

    private const val TOTAL_NUMBER = 56f
    private const val TOTAL_LABEL = 22f
    private const val TOTAL_GAP = 4f
    private const val TOTAL_COLUMN_GAP = 16f
    private const val TOTALS_GAP = 32f
    private const val SQUARE_TOTALS_FRACTION = 0.54f
    private const val TOTALS = 3
    private const val NARROW_ROW_FRACTION = 0.6f

    private const val AVATAR = 132f
    private const val AVATAR_MIN_GAP = 28f
    private const val AVATAR_NAME_GAP = 14f
    private const val AVATAR_NAME_INSET = 12f
    private const val ARTIST_NAME = 26f
    private const val BADGE_FRACTION = 0.28f

    private const val STORY_ROW = 116f
    private const val COLUMN_ROW = 112f
    private const val COLUMN_GAP = 40f
    private const val MAX_ROW_FRACTION = 0.24f

    /** Row height at which a single-ranking header is drawn at scale 1. */
    private const val HEADER_ROW_REFERENCE = 180f

    private const val TILE_GAP = 20f
    private const val TILE_TEXT_GAP = 14f
    private const val TILE_TITLE = 26f
    private const val TILE_SUBTITLE = 22f

    // Ranked row proportions, relative to the row height.
    private const val THUMB_FRACTION = 0.74f
    private const val TITLE_FRACTION = 0.26f
    private const val TEXT_GAP_FRACTION = 0.2f
    private const val MIN_ROW_TITLE = 27f
    private const val MAX_ROW_TITLE = 46f
    private const val SUBTITLE_RATIO = 0.8f
    private const val RANK_RATIO = 1.05f
    private const val LINE_GAP_RATIO = 0.18f
    private const val ART_RADIUS = 0.12f
}
