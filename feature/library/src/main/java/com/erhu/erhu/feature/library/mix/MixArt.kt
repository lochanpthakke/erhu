// Generative art: positions, sizes, alphas and colours are the design itself, not magic numbers.
@file:Suppress("MagicNumber")

package com.erhu.erhu.feature.library.mix

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Original cover art for the smart mixes, generated from a seed: no album art, photos, images
 * or network. [MixArt.generate] turns a mix into a list of [ArtLayer]s in unit coordinates
 * (0..1 across the square cover); [MixArtwork] draws them. The seed comes from the mix's identity,
 * so a mix always looks the same, across recompositions and launches; different mixes get
 * different palettes and compositions.
 */
data class MixArt(
    val palette: ArtPalette,
    val composition: Composition,
    val layers: List<ArtLayer>,
) {
    enum class Composition { RIBBONS, BLOBS, CONTOURS, GEOMETRIC, PARTICLES, BURST }

    companion object {
        /** The art for [kind]: its own art direction, picked within by the kind's seed. */
        fun generate(kind: MixKind): MixArt = generate(seedOf(kind.key), direction(kind))

        /**
         * The same art composed for a wide banner [aspect] (width / height) with text on its left:
         * the palette and composition of [kind]'s cover (same seed), laid out across the banner's
         * own width, the focus to the right and the text side kept to the backdrop and soft light.
         */
        fun banner(
            kind: MixKind,
            aspect: Float,
        ): MixArt = generate(seedOf(kind.key), direction(kind), Frame(aspect, clear = BANNER_TEXT_SIDE))

        fun generate(
            seed: Long,
            direction: Direction,
            frame: Frame = Frame.COVER,
        ): MixArt {
            val random = Random(seed)
            val palette = direction.palettes[random.nextInt(direction.palettes.size)]
            val composition = direction.compositions[random.nextInt(direction.compositions.size)]
            val layers = ArtComposer(random, palette, frame).compose(composition)
            return MixArt(palette, composition, layers)
        }

        /** The share of a banner's width kept clear for its text. */
        const val BANNER_TEXT_SIDE = 0.46f

        /** A stable 64-bit seed for a mix key (FNV-1a; never changes between runs or devices). */
        fun seedOf(key: String): Long {
            var hash = -0x340d631b7bdddcdbL
            for (ch in key) {
                hash = hash xor ch.code.toLong()
                hash *= 0x100000001b3L
            }
            return hash
        }

        /** One branch per mix: a lookup table, not branching logic. */
        @Suppress("CyclomaticComplexMethod")
        fun direction(kind: MixKind): Direction =
            when (kind) {
                MixKind.HIGH_ENERGY ->
                    Direction(
                        listOf(Palettes.SUNRISE, Palettes.CITRUS, Palettes.EMBER, Palettes.BUBBLEGUM),
                        listOf(Composition.BURST, Composition.GEOMETRIC),
                    )
                MixKind.LATE_NIGHT ->
                    Direction(
                        listOf(Palettes.MIDNIGHT, Palettes.VIOLET_DUSK),
                        listOf(Composition.PARTICLES, Composition.CONTOURS),
                    )
                MixKind.CHILL ->
                    Direction(
                        listOf(Palettes.MINT, Palettes.LAGOON, Palettes.SKY, Palettes.PEACH),
                        listOf(Composition.BLOBS, Composition.RIBBONS),
                    )
                MixKind.DEEP_CUTS ->
                    Direction(
                        listOf(Palettes.EMBER, Palettes.MEADOW, Palettes.VIOLET_DUSK, Palettes.LAGOON),
                        listOf(Composition.CONTOURS, Composition.PARTICLES, Composition.GEOMETRIC),
                    )
                MixKind.ROTATION ->
                    Direction(
                        listOf(Palettes.CITRUS, Palettes.SUNRISE, Palettes.BUBBLEGUM),
                        listOf(Composition.GEOMETRIC, Composition.BURST),
                    )
                MixKind.YOUR_SOUND ->
                    Direction(
                        listOf(Palettes.SKY, Palettes.VIOLET_DUSK, Palettes.LAGOON),
                        listOf(Composition.RIBBONS, Composition.CONTOURS),
                    )
                MixKind.FORGOTTEN ->
                    Direction(
                        listOf(Palettes.PEACH, Palettes.BURGUNDY, Palettes.SKY),
                        listOf(Composition.BLOBS, Composition.PARTICLES),
                    )
                MixKind.A_LITTLE_DIFFERENT ->
                    Direction(
                        listOf(Palettes.MEADOW, Palettes.BUBBLEGUM, Palettes.MINT),
                        listOf(Composition.GEOMETRIC, Composition.BLOBS),
                    )
                // A spotlight: one bright focus with light falling around it, in warm stage tones.
                MixKind.SPOTLIGHT -> Direction(listOf(Palettes.SOUL, Palettes.BURGUNDY), listOf(Composition.CONTOURS))
                // Grooves of a record in faded, sun-bleached colour.
                MixKind.THROWBACKS -> Direction(listOf(Palettes.VINTAGE), listOf(Composition.CONTOURS))
                // Short and punchy: bold, compact forms.
                MixKind.QUICK_HITS -> Direction(listOf(Palettes.CITRUS, Palettes.EMBER), listOf(Composition.GEOMETRIC, Composition.BURST))
                // Long, unhurried waves under a night sky.
                MixKind.LONG_LISTENS -> Direction(listOf(Palettes.NIGHT_DRIVE, Palettes.SLATE), listOf(Composition.RIBBONS))
                // Unknown territory: a drift of distant points.
                MixKind.YET_TO_HEAR -> Direction(listOf(Palettes.MIDNIGHT, Palettes.NIGHT_DRIVE), listOf(Composition.PARTICLES))
                MixKind.FAVOURITES -> Direction(listOf(Palettes.BUBBLEGUM, Palettes.SUNRISE), listOf(Composition.BLOBS))
                MixKind.FRESH -> Direction(listOf(Palettes.MEADOW, Palettes.LAGOON), listOf(Composition.RIBBONS))
            }
    }

    /**
     * Where the art is drawn: [aspect] = width / height (1 for a cover); the left [clear] share of
     * the width is kept for text (only the backdrop and soft light reach into it). Positions are in
     * 0..1 of the width and height, sizes in 0..1 of the shorter side, so shapes never stretch.
     */
    data class Frame(
        val aspect: Float,
        val clear: Float,
    ) {
        companion object {
            val COVER = Frame(aspect = 1f, clear = 0f)
        }
    }

    /** A mix's art direction: the palettes and compositions its cover may use. */
    data class Direction(
        val palettes: List<ArtPalette>,
        val compositions: List<Composition>,
    )
}

/** Colours as ARGB. [background] runs top to bottom; [accents] are drawn over it. */
data class ArtPalette(
    val name: String,
    val background: List<Int>,
    val accents: List<Int>,
    val dark: Boolean,
)

/** One drawing primitive, in unit coordinates of the cover. */
sealed interface ArtLayer {
    /** Linear gradient over the whole cover, at [angleDeg]. */
    data class Backdrop(
        val colors: List<Int>,
        val angleDeg: Float,
    ) : ArtLayer

    /** Soft radial light. */
    data class Glow(
        val x: Float,
        val y: Float,
        val radius: Float,
        val color: Int,
        val alpha: Float,
    ) : ArtLayer

    /** An organic closed shape: a circle whose radius varies smoothly by [wobble] around it. */
    data class Blob(
        val x: Float,
        val y: Float,
        val radius: Float,
        val wobble: List<Float>,
        val rotation: Float,
        val color: Int,
        val alpha: Float,
    ) : ArtLayer

    /** A smooth flowing stroke through [points] (x, y pairs). */
    data class Ribbon(
        val points: List<Float>,
        val width: Float,
        val color: Int,
        val alpha: Float,
    ) : ArtLayer

    /** A contour line: a wobbly ring. */
    data class Ring(
        val x: Float,
        val y: Float,
        val radius: Float,
        val wobble: List<Float>,
        val stroke: Float,
        val color: Int,
        val alpha: Float,
    ) : ArtLayer

    data class Disc(
        val x: Float,
        val y: Float,
        val radius: Float,
        val color: Int,
        val alpha: Float,
    ) : ArtLayer

    /** A filled circular sector (quarter / half discs of the geometric covers). */
    data class Sector(
        val x: Float,
        val y: Float,
        val radius: Float,
        val startDeg: Float,
        val sweepDeg: Float,
        val color: Int,
        val alpha: Float,
    ) : ArtLayer

    /** A filled polygon through [points] (x, y pairs). */
    data class Shard(
        val points: List<Float>,
        val color: Int,
        val alpha: Float,
    ) : ArtLayer

    data class Block(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val corner: Float,
        val color: Int,
        val alpha: Float,
    ) : ArtLayer
}

/** The palettes: bright and fresh as well as deep and atmospheric. */
object Palettes {
    private fun c(hex: Long) = hex.toInt()

    val SUNRISE =
        ArtPalette(
            "Sunrise",
            listOf(c(0xFFFFD23F), c(0xFFFF9F1C)),
            listOf(c(0xFFFF6F59), c(0xFFFFF3D6), c(0xFFEF476F), c(0xFFFFB627)),
            dark = false,
        )
    val CITRUS =
        ArtPalette(
            "Citrus",
            listOf(c(0xFFFFF1C1), c(0xFFFFC94D)),
            listOf(c(0xFFFB8500), c(0xFFFF5D73), c(0xFF219EBC), c(0xFF023047)),
            dark = false,
        )
    val MINT =
        ArtPalette(
            "Mint",
            listOf(c(0xFFE8FFF6), c(0xFFB8F2E6)),
            listOf(c(0xFF2EC4B6), c(0xFF7BE0AD), c(0xFF0F8B8D), c(0xFFFFE5A5)),
            dark = false,
        )
    val LAGOON =
        ArtPalette(
            "Lagoon",
            listOf(c(0xFF90E0EF), c(0xFF0096C7)),
            listOf(c(0xFFCAF0F8), c(0xFF48CAE4), c(0xFF023E8A), c(0xFFFFD6A5)),
            dark = false,
        )
    val MEADOW =
        ArtPalette(
            "Meadow",
            listOf(c(0xFFFCEFB4), c(0xFFA7E8BD)),
            listOf(c(0xFF4CAF50), c(0xFF2D6A4F), c(0xFFF77F00), c(0xFF5FA8D3)),
            dark = false,
        )
    val SKY =
        ArtPalette(
            "Sky",
            listOf(c(0xFFD7E9FF), c(0xFFB8B8FF)),
            listOf(c(0xFF3A86FF), c(0xFF6A4C93), c(0xFFFF8FAB), c(0xFFFFFFFF)),
            dark = false,
        )
    val BUBBLEGUM =
        ArtPalette(
            "Bubblegum",
            listOf(c(0xFFFFF1E6), c(0xFFFFC8DD)),
            listOf(c(0xFFFF5D8F), c(0xFF7B2CBF), c(0xFF2EC4B6), c(0xFFFFB703)),
            dark = false,
        )
    val PEACH =
        ArtPalette(
            "Peach",
            listOf(c(0xFFFFE5D9), c(0xFFFFCAD4)),
            listOf(c(0xFFF4978E), c(0xFFF8AD9D), c(0xFF9D8189), c(0xFFFFF8F0)),
            dark = false,
        )
    val MIDNIGHT =
        ArtPalette(
            "Midnight",
            listOf(c(0xFF0B132B), c(0xFF1C2541)),
            listOf(c(0xFF5BC0BE), c(0xFF3A86FF), c(0xFFFFD166), c(0xFFE0FBFC)),
            dark = true,
        )
    val VIOLET_DUSK =
        ArtPalette(
            "Violet dusk",
            listOf(c(0xFF240046), c(0xFF5A189A)),
            listOf(c(0xFF9D4EDD), c(0xFFE0AAFF), c(0xFFFF8FAB), c(0xFF4CC9F0)),
            dark = true,
        )
    val BURGUNDY =
        ArtPalette(
            "Burgundy",
            listOf(c(0xFF2B0A12), c(0xFF6A0F2B)),
            listOf(c(0xFFC0365A), c(0xFFF7B267), c(0xFFFFE8D6), c(0xFFE36588)),
            dark = true,
        )
    val EMBER =
        ArtPalette(
            "Ember",
            listOf(c(0xFF1F1F24), c(0xFF34343E)),
            listOf(c(0xFFFF7A45), c(0xFFFFD166), c(0xFF06D6A0), c(0xFFF5F5F5)),
            dark = true,
        )

    /** Deep blue into muted violet, with restrained warm light. */
    val NIGHT_DRIVE =
        ArtPalette(
            "Night drive",
            listOf(c(0xFF101A3A), c(0xFF2E2350)),
            listOf(c(0xFFFFB86B), c(0xFF6C7BD9), c(0xFFE07A5F), c(0xFFF2E9DC)),
            dark = true,
        )

    /** Warm brown and muted gold. */
    val SOUL =
        ArtPalette(
            "Soul",
            listOf(c(0xFF2A1A12), c(0xFF4A2E1E)),
            listOf(c(0xFFD9A441), c(0xFFF3D9A4), c(0xFFB5523B), c(0xFF8C6A4F)),
            dark = true,
        )

    /** Sun-bleached cream, faded orange and teal. */
    val VINTAGE =
        ArtPalette(
            "Vintage",
            listOf(c(0xFFF4E7CF), c(0xFFE9CFA3)),
            listOf(c(0xFFD9733B), c(0xFF2F7F7A), c(0xFF8E3B2E), c(0xFFF1B24A)),
            dark = false,
        )

    /** Cool slate and muted blue. */
    val SLATE =
        ArtPalette(
            "Slate",
            listOf(c(0xFF1E2A33), c(0xFF34495A)),
            listOf(c(0xFF8FB3C9), c(0xFFC9D6DF), c(0xFF5D7A8C), c(0xFFE8C07D)),
            dark = true,
        )

    val ALL =
        listOf(
            SUNRISE,
            CITRUS,
            MINT,
            LAGOON,
            MEADOW,
            SKY,
            BUBBLEGUM,
            PEACH,
            MIDNIGHT,
            VIOLET_DUSK,
            BURGUNDY,
            EMBER,
            NIGHT_DRIVE,
            SOUL,
            VINTAGE,
            SLATE,
        )
}

/** Lays out one composition with a seeded random source. */
private class ArtComposer(
    private val random: Random,
    private val palette: ArtPalette,
    private val frame: MixArt.Frame = MixArt.Frame.COVER,
) {
    /** A horizontal position for a focal element: [from]..[to] of the width beside the text side. */
    private fun fx(
        from: Float,
        to: Float,
    ) = frame.clear + range(from, to) * (1f - frame.clear)

    /** A horizontal length in shape units (the shorter side) as a share of the width. */
    private fun wide(length: Float) = length / frame.aspect

    /** How far (in shape units) a shape at [x] may reach to the left before entering the text side. */
    private fun leftReach(x: Float) = if (frame.clear <= 0f) Float.MAX_VALUE else (x - frame.clear * TEXT_EDGE) * frame.aspect

    private val layers = mutableListOf<ArtLayer>()

    fun compose(composition: MixArt.Composition): List<ArtLayer> {
        layers += ArtLayer.Backdrop(palette.background, angleDeg = range(20f, 160f))
        when (composition) {
            MixArt.Composition.RIBBONS -> ribbons()
            MixArt.Composition.BLOBS -> blobs()
            MixArt.Composition.CONTOURS -> contours()
            MixArt.Composition.GEOMETRIC -> geometric()
            MixArt.Composition.PARTICLES -> particles()
            MixArt.Composition.BURST -> burst()
        }
        return layers.toList()
    }

    private fun accent(i: Int) = palette.accents[Math.floorMod(i, palette.accents.size)]

    private fun anyAccent() = palette.accents[random.nextInt(palette.accents.size)]

    private fun range(
        from: Float,
        to: Float,
    ) = from + random.nextFloat() * (to - from)

    private fun wobble(
        points: Int,
        amount: Float,
    ) = List(points) { 1f + range(-amount, amount) }

    private fun glow(alpha: Float = 0.55f) {
        layers += ArtLayer.Glow(fx(0.15f, 0.85f), range(0.1f, 0.6f), range(0.45f, 0.8f), anyAccent(), alpha)
    }

    /** Flowing waves across the cover, like sound. */
    private fun ribbons() {
        glow(0.35f)
        val count = random.nextInt(3, 6)
        val baseY = range(0.25f, 0.4f)
        val spacing = range(0.08f, 0.14f)
        val frequency = range(1.2f, 2.4f)
        // On a banner the waves rise out of the text side's edge and run off to the right.
        val start = if (frame.clear > 0f) frame.clear * TEXT_EDGE else -0.1f
        for (i in 0 until count) {
            val amplitude = range(0.05f, 0.14f)
            val phase = range(0f, (2 * PI).toFloat())
            val y0 = baseY + i * spacing
            val points =
                (0..16).flatMap { step ->
                    val x = start + step * (1.1f - start) / 16
                    listOf(x, y0 + amplitude * sin(phase + x * frequency * 2 * PI.toFloat()))
                }
            layers += ArtLayer.Ribbon(points, width = range(0.035f, 0.11f), color = accent(i), alpha = range(0.75f, 0.95f))
        }
        dust(random.nextInt(6, 12), 0.006f, 0.014f)
    }

    /** Large overlapping organic forms. */
    private fun blobs() {
        glow(0.4f)
        val count = random.nextInt(3, 5)
        for (i in 0 until count) {
            val x = fx(0.15f, 0.85f)
            val y = range(0.15f, 0.85f)
            layers +=
                ArtLayer.Blob(
                    x = x,
                    y = y,
                    // A banner's forms stay out of its text side; a cover's are unchanged.
                    radius = range(0.22f, 0.42f).coerceAtMost(leftReach(x)),
                    wobble = wobble(8, 0.18f),
                    rotation = range(0f, 360f),
                    color = accent(i),
                    alpha = range(0.7f, 0.95f),
                )
        }
        repeat(random.nextInt(2, 5)) {
            layers += ArtLayer.Disc(fx(0.1f, 0.9f), range(0.1f, 0.9f), range(0.02f, 0.06f), anyAccent(), 0.9f)
        }
    }

    /** Concentric contour lines around an off-centre point. */
    private fun contours() {
        glow(0.45f)
        val x = fx(0.25f, 0.75f)
        val y = range(0.25f, 0.75f)
        layers += ArtLayer.Disc(x, y, range(0.05f, 0.1f), accent(0), 0.95f)
        val count = random.nextInt(7, 12)
        val step = range(0.055f, 0.08f)
        val shape = wobble(7, 0.12f)
        for (i in 1..count) {
            val drift = wobble(7, 0.04f)
            // Rings stop where they would reach into the text side.
            if (0.06f + i * step > leftReach(x)) break
            layers +=
                ArtLayer.Ring(
                    x = x,
                    y = y,
                    radius = 0.06f + i * step,
                    wobble = shape.zip(drift) { a, b -> a * b },
                    stroke = range(0.006f, 0.016f),
                    color = accent(i),
                    alpha = (1f - i / (count + 2f)).coerceIn(0.3f, 0.95f),
                )
        }
    }

    /** A grid of bold circles, half and quarter discs and blocks. */
    private fun geometric() {
        val rows = 3
        val size = 1f / rows
        // Square cells (in shape units): on a banner, as many columns as fit beside the text side.
        val cellWidth = wide(size)
        val columns = ((1f - frame.clear) / cellWidth + COLUMN_EPSILON).toInt().coerceAtLeast(1)
        // A cover's grid starts exactly at 0 (as it always has); a banner's ends at the right edge.
        val left = if (frame.clear <= 0f) 0f else 1f - columns * cellWidth
        for (row in 0 until rows) {
            for (column in 0 until columns) cell(left + column * cellWidth, row * size, cellWidth, size)
        }
        repeat(random.nextInt(1, 4)) {
            layers += ArtLayer.Disc(fx(0.05f, 0.95f), range(0.05f, 0.95f), range(0.015f, 0.035f), anyAccent(), 1f)
        }
    }

    /** One grid cell of the geometric cover: an optional backing tile, then a bold primitive in another accent. */
    private fun cell(
        left: Float,
        top: Float,
        width: Float,
        size: Float,
    ) {
        val backing = random.nextInt(palette.accents.size)
        if (random.nextFloat() < 0.55f) layers += ArtLayer.Block(left, top, left + width, top + size, 0f, accent(backing), 0.92f)
        val color = accent(backing + 1 + random.nextInt(palette.accents.size - 1))
        val centreX = left + width / 2
        val centreY = top + size / 2
        layers +=
            when (random.nextInt(5)) {
                0 -> ArtLayer.Disc(centreX, centreY, size * range(0.34f, 0.46f), color, 1f)
                1 -> {
                    // A quarter disc anchored in one of the cell's corners, opening into the cell.
                    val corner = random.nextInt(4)
                    val cx = left + if (corner % 2 == 0) 0f else width
                    val cy = top + if (corner < 2) 0f else size
                    ArtLayer.Sector(cx, cy, size, 90f * QUARTER_STARTS[corner], 90f, color, 1f)
                }
                2 -> ArtLayer.Sector(centreX, centreY, size / 2, 90f * random.nextInt(4), 180f, color, 1f)
                3 -> {
                    val inset = size * 0.14f
                    val insetX = wide(inset)
                    ArtLayer.Block(left + insetX, top + inset, left + width - insetX, top + size - inset, size * 0.2f, color, 1f)
                }
                else -> ArtLayer.Ring(centreX, centreY, size * 0.3f, List(6) { 1f }, size * 0.12f, color, 1f)
            }
    }

    /** A drift of light points along a curved band, with a couple of bright stars. */
    private fun particles() {
        glow(0.6f)
        glow(0.4f)
        val tilt = range(-0.4f, 0.4f)
        val bend = range(0.1f, 0.3f)

        fun bandY(t: Float) = 0.5f + tilt * (t - 0.5f) + bend * sin(t * PI.toFloat()) - bend / 2

        // Band position t (0..1) to x: across the whole cover, or the banner beside its text.
        fun bandX(t: Float) = frame.clear + t * (1f - frame.clear)

        // Soft forms along the band give the drift a body.
        repeat(3) { i ->
            val t = 0.2f + i * 0.3f + range(-0.08f, 0.08f)
            layers +=
                ArtLayer.Blob(bandX(t), bandY(t), range(0.14f, 0.22f), wobble(8, 0.2f), range(0f, 360f), accent(i), range(0.22f, 0.35f))
        }
        val count = random.nextInt(110, 160)
        for (i in 0 until count) {
            val t = random.nextFloat()
            val spread = (random.nextFloat() - 0.5f) * range(0.12f, 0.4f)
            layers += ArtLayer.Disc(bandX(t), bandY(t) + spread, range(0.004f, 0.017f), accent(i), range(0.5f, 1f))
        }
        repeat(random.nextInt(2, 4)) {
            val x = fx(0.15f, 0.85f)
            val y = range(0.15f, 0.85f)
            layers += ArtLayer.Glow(x, y, range(0.08f, 0.14f), accent(3), 0.6f)
            layers += ArtLayer.Disc(x, y, range(0.012f, 0.02f), accent(3), 1f)
        }
    }

    /** Shards radiating from an off-centre focus, with a bold disc: energy. */
    private fun burst() {
        val x = fx(0.3f, 0.7f)
        val y = range(0.3f, 0.7f)
        glow(0.5f)
        val rays = random.nextInt(10, 17)
        val offset = range(0f, 360f)
        for (i in 0 until rays) {
            val angle = Math.toRadians((offset + i * 360f / rays + range(-6f, 6f)).toDouble())
            val width = Math.toRadians(range(4f, 11f).toDouble())
            // Same draw order as the cover always had (reach, then inner), so covers stay unchanged.
            val reach = range(0.55f, 0.95f)
            val inner = range(0.08f, 0.16f)
            // Rays pointing at the text side end before it.
            val leftward = -cos(angle).toFloat()
            val length = if (leftward > 0f) reach.coerceAtMost(leftReach(x) / leftward) else reach
            if (length <= inner) continue
            layers +=
                ArtLayer.Shard(
                    listOf(
                        x + wide(inner * cos(angle - width).toFloat()),
                        y + inner * sin(angle - width).toFloat(),
                        x + wide(length * cos(angle).toFloat()),
                        y + length * sin(angle).toFloat(),
                        x + wide(inner * cos(angle + width).toFloat()),
                        y + inner * sin(angle + width).toFloat(),
                    ),
                    accent(i),
                    range(0.7f, 0.95f),
                )
        }
        layers += ArtLayer.Disc(x, y, range(0.1f, 0.16f), accent(1), 1f)
        layers += ArtLayer.Ring(x, y, range(0.2f, 0.26f), List(6) { 1f }, 0.012f, accent(3), 0.9f)
        dust(random.nextInt(8, 14), 0.008f, 0.02f)
    }

    private fun dust(
        count: Int,
        from: Float,
        to: Float,
    ) {
        repeat(count) { layers += ArtLayer.Disc(fx(0.05f, 0.95f), range(0.05f, 0.95f), range(from, to), anyAccent(), range(0.6f, 1f)) }
    }

    private companion object {
        /** Shapes may reach this far into the text side (a share of it), so the two sides meet softly. */
        const val TEXT_EDGE = 0.85f

        /** 1 / (1 / 3) is 2.9999998 in floats: the cover must still get its three columns. */
        const val COLUMN_EPSILON = 1e-3f

        /** Start angle (in quarter turns) of the quarter disc anchored at each cell corner. */
        val QUARTER_STARTS = intArrayOf(0, 1, 3, 2)
    }
}
