package com.erhu.erhu.feature.library

import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.component.pressScale
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.designsystem.theme.LocalGlassTokens
import com.erhu.erhu.feature.library.mix.ArtPalette
import com.erhu.erhu.feature.library.mix.MixArt
import com.erhu.erhu.feature.library.mix.MixArtCanvas
import com.erhu.erhu.feature.library.mix.MixKind
import com.erhu.erhu.feature.library.mix.SmartMix
import kotlin.random.Random
import com.erhu.erhu.core.common.R as CommonR

/*
 * The home screen's hero: the Smart Playlists as one large banner, a mix at a time, swiped
 * sideways. Every banner follows the same rules:
 * - the art fills the whole banner and is composed for it (MixArt.banner): its focus sits to the
 *   right, its left side, where the text goes, carries only the backdrop and soft light;
 * - the text is light on dark palettes and dark on light ones, and a veil of the palette's own
 *   tone fades in behind it only as much as contrast needs (none at all when the art is calm);
 * - the same type, spacing, corner radius and Play button on every banner.
 */

/** The banner's proportions: wide, never taller than this (or shorter than that). */
private const val BANNER_ASPECT = 1.75f
private val BANNER_MIN_HEIGHT = 168.dp
private val BANNER_MAX_HEIGHT = 240.dp

/** At most this many concepts in one session's carousel. */
private const val MAX_BANNERS = 6

/** The text column's share of the banner's width (the art's clear side, a little more). */
private const val TEXT_SHARE = 0.54f

private val PLAY_HEIGHT = 40.dp
private val PLAY_ICON = 20.dp
private const val BANNER_PRESSED_SCALE = 0.98f
private val DOT_SIZE = 6.dp
private val DOT_ACTIVE_WIDTH = 18.dp
private const val DOT_INACTIVE_ALPHA = 0.32f

/** Under the Glass Effect: a fine light rim and a soft sheen on the upper part. */
private val GLASS_RIM = 1.dp
private const val GLASS_RIM_TOP = 0.34f
private const val GLASS_RIM_BOTTOM = 0.06f
private const val GLASS_SHEEN = 0.08f
private const val GLASS_SHEEN_END = 0.35f

/** The veil holds its strength under the text, eases off beyond it and is gone well before the right edge. */
private const val VEIL_HOLD = 0.5f
private const val VEIL_EASE = 0.64f
private const val VEIL_EASE_SHARE = 0.4f
private const val VEIL_END = 0.82f

/**
 * The Smart Playlists hero: a pager of [mixes] in this session's order (and, while history is
 * short, a last page saying personal mixes come with listening). Tapping a banner opens the mix;
 * its Play button plays it. Swiping is manual: no timer runs in the background.
 */
@Composable
internal fun SmartMixHero(
    mixes: List<SmartMix>,
    needsMoreListening: Boolean,
    onOpen: (mixKey: String) -> Unit,
    onPlay: (mixKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val featured = remember(mixes) { HomeSession.feature(mixes, MAX_BANNERS) }
    val pageCount = featured.size + if (needsMoreListening) 1 else 0
    if (pageCount == 0) return
    val pager = rememberPagerState { pageCount }
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = Spacing.screenGutter),
            pageSpacing = Spacing.md,
            key = { page -> featured.getOrNull(page)?.kind?.key ?: "more-listening" },
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Wide on every phone, never towering on a tablet or squashed on a small screen.
                val height = (maxWidth / BANNER_ASPECT).coerceIn(BANNER_MIN_HEIGHT, BANNER_MAX_HEIGHT)
                val bannerModifier = Modifier.fillMaxWidth().height(height)
                val mix = featured.getOrNull(page)
                if (mix != null) {
                    MixBanner(
                        mix = mix,
                        aspect = maxWidth / height,
                        onOpen = { onOpen(mix.kind.key) },
                        onPlay = { onPlay(mix.kind.key) },
                        modifier = bannerModifier,
                    )
                } else {
                    MoreListeningBanner(modifier = bannerModifier)
                }
            }
        }
        if (pageCount > 1) {
            PageDots(count = pageCount, current = pager.currentPage, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/**
 * Which concepts lead the carousel: a fixed order for this app session (a new one each launch),
 * so the banner is fresh from one visit of the app to the next but never reshuffles while it is
 * in use. Ranked by key, so a rebuilt mix list keeps the order of the mixes it still has.
 */
internal object HomeSession {
    private val seed = Random.nextLong()

    fun feature(
        mixes: List<SmartMix>,
        limit: Int,
        sessionSeed: Long = seed,
    ): List<SmartMix> = mixes.sortedBy { FeaturedAlbums.dailyRank(MixArt.seedOf(it.kind.key), sessionSeed) }.take(limit)
}

/** Composed banner art by mix and shape: each is laid out once, reused when the concept returns. */
private object BannerArtCache {
    private const val ENTRIES = 24

    /** Banners of nearly the same shape share their art. */
    private const val ASPECT_STEPS = 20f

    private val cache = LruCache<String, Pair<MixArt, BannerLook>>(ENTRIES)

    fun get(
        kind: MixKind,
        aspect: Float,
    ): Pair<MixArt, BannerLook> {
        val bucket = (aspect * ASPECT_STEPS).toInt() / ASPECT_STEPS
        val key = "${kind.key}@$bucket"
        return cache.get(key) ?: MixArt.banner(kind, bucket).let { art -> art to BannerLook.of(art.palette) }.also { cache.put(key, it) }
    }
}

/** One mix as a banner: its art across the whole banner, the text and Play in its clear side. */
@Composable
private fun MixBanner(
    mix: SmartMix,
    aspect: Float,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (art, look) = remember(mix.kind, aspect) { BannerArtCache.get(mix.kind, aspect) }
    val press = remember { MutableInteractionSource() }
    BannerSurface(
        modifier =
            modifier
                .pressScale(press, BANNER_PRESSED_SCALE)
                .clickable(
                    interactionSource = press,
                    indication = LocalIndication.current,
                    onClickLabel = stringResource(CommonR.string.smart_playlist_cd_open, mix.localizedTitle()),
                    onClick = onOpen,
                ),
    ) {
        MixArtCanvas(art = art, description = null, modifier = Modifier.fillMaxSize())
        if (look.veilAlpha > 0f) {
            Box(
                modifier =
                    Modifier.fillMaxSize().drawWithCache {
                        val veil = Color(look.veil)
                        val brush =
                            Brush.horizontalGradient(
                                0f to veil.copy(alpha = look.veilAlpha),
                                VEIL_HOLD to veil.copy(alpha = look.veilAlpha),
                                VEIL_EASE to veil.copy(alpha = look.veilAlpha * VEIL_EASE_SHARE),
                                VEIL_END to veil.copy(alpha = 0f),
                            )
                        onDrawBehind { drawRect(brush) }
                    },
            )
        }
        BannerText(mix = mix, look = look, onPlay = onPlay)
    }
}

/** The banner's text side: what it is, its name, how it was made; then Play and the song count. */
@Composable
private fun BannerText(
    mix: SmartMix,
    look: BannerLook,
    onPlay: () -> Unit,
) {
    val text = Color(look.text)
    val quiet = text.copy(alpha = BannerLook.SECONDARY_ALPHA)
    val displayTitle = mix.localizedTitle()
    val displayDescription = mix.localizedDescription()
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(TEXT_SHARE)
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(text = stringResource(CommonR.string.smart_playlist_badge), style = MaterialTheme.typography.labelSmall, color = quiet)
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(text = displayDescription, style = TextStyles.tileSubtitle, color = quiet, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PlayPill(title = displayTitle, look = look, onClick = onPlay)
            Text(text = songCountText(mix.songIds.size), style = TextStyles.tileSubtitle, color = quiet, maxLines = 1)
        }
    }
}

/** While history is short: the honest note, in the banner's shape and the theme's own colours. */
@Composable
private fun MoreListeningBanner(modifier: Modifier = Modifier) {
    BannerSurface(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, Radius.largeShape)) {
        Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.Center) {
            Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(CommonR.string.smart_playlist_more_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(CommonR.string.smart_playlist_more_body),
                style = TextStyles.tileSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The banner's frame: rounded like the app's other large cards. Under the Glass Effect the art is
 * finished with the glass material's edge (a fine light rim, a soft sheen on top), drawn over the
 * art so it is never hidden by it; without it the art stands on its own.
 */
@Composable
private fun BannerSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlassTokens.current != null
    Box(
        modifier =
            modifier
                .clip(Radius.largeShape)
                .then(if (glass) Modifier.glassEdge() else Modifier),
        content = content,
    )
}

private fun Modifier.glassEdge(): Modifier =
    drawWithCache {
        val corner = CornerRadius(Radius.large.toPx())
        val stroke = GLASS_RIM.toPx()
        val rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = GLASS_RIM_TOP), Color.White.copy(alpha = GLASS_RIM_BOTTOM)))
        val sheen = Brush.verticalGradient(0f to Color.White.copy(alpha = GLASS_SHEEN), GLASS_SHEEN_END to Color.White.copy(alpha = 0f))
        onDrawWithContent {
            drawContent()
            drawRect(sheen)
            // The stroke is centred on the edge and the banner is clipped: double it.
            drawRoundRect(rim, cornerRadius = corner, style = Stroke(width = stroke * 2))
        }
    }

/** Play: compact and obvious, in the banner's text colour (light on dark art, dark on light). */
@Composable
private fun PlayPill(
    title: String,
    look: BannerLook,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val playDescription = stringResource(CommonR.string.smart_playlist_cd_play, title)
    Row(
        modifier =
            Modifier
                .height(PLAY_HEIGHT)
                .pressScale(press)
                .clip(Radius.pillShape)
                .background(Color(look.text))
                .clickable(interactionSource = press, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = playDescription }
                .padding(start = Spacing.md, end = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color(look.onText),
            modifier = Modifier.size(PLAY_ICON),
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            text = stringResource(CommonR.string.action_play),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color(look.onText),
        )
    }
}

/** Which page is showing: small dots, the current one longer, in the theme's colours. */
@Composable
private fun PageDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) DOT_ACTIVE_WIDTH else DOT_SIZE, label = "heroDot")
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = DOT_INACTIVE_ALPHA),
                label = "heroDotColor",
            )
            Box(modifier = Modifier.size(width = width, height = DOT_SIZE).clip(Radius.pillShape).background(color))
        }
    }
}

/**
 * How a banner's text sits on its art: [text] (light on a dark palette, dark on a light one), the
 * [veil] tone of the palette behind it and how strong it must be ([veilAlpha], 0 = no veil) for
 * the text to keep [TARGET_CONTRAST] against every colour that can appear on the text side: the
 * backdrop colours and the accents' soft glow over them. [onText] is the Play button's content.
 */
@Immutable
internal data class BannerLook(
    val text: Int,
    val onText: Int,
    val veil: Int,
    val veilAlpha: Float,
) {
    companion object {
        /** Above 4.5:1 so the secondary lines (at [SECONDARY_ALPHA]) still read at body-text contrast. */
        const val TARGET_CONTRAST = 6.0
        const val SECONDARY_ALPHA = 0.8f

        /** How strongly an accent's glow can tint the text side, at most. */
        private const val GLOW_SHARE = 0.45f

        /** The veil: the palette's first backdrop colour taken this far towards black (or white). */
        private const val VEIL_DEPTH = 0.62f
        private const val MAX_VEIL = 0.92f
        private const val VEIL_STEP = 0.02f

        private const val LIGHT_TEXT = 0xFFF7F3F2.toInt()
        private const val DARK_TEXT = 0xFF17141A.toInt()
        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val BYTE = 0xFF
        private const val RED = 16
        private const val GREEN = 8

        fun of(palette: ArtPalette): BannerLook {
            val lightText = palette.dark
            val text = if (lightText) LIGHT_TEXT else DARK_TEXT
            val veil = mix(if (lightText) BLACK else WHITE, palette.background.first(), VEIL_DEPTH)
            val textSide = palette.background + palette.accents.flatMap { accent -> palette.background.map { mix(accent, it, GLOW_SHARE) } }

            fun readable(alpha: Float) = textSide.all { contrast(text, mix(veil, it, alpha)) >= TARGET_CONTRAST }
            var alpha = 0f
            while (alpha < MAX_VEIL && !readable(alpha)) alpha += VEIL_STEP
            return BannerLook(
                text,
                onText = if (lightText) DARK_TEXT else LIGHT_TEXT,
                veil = veil,
                veilAlpha = alpha.coerceAtMost(MAX_VEIL),
            )
        }

        /** [top] over [bottom] at [share] (opaque result). */
        internal fun mix(
            top: Int,
            bottom: Int,
            share: Float,
        ): Int {
            fun channel(shift: Int) =
                ((((top shr shift) and BYTE) * share + ((bottom shr shift) and BYTE) * (1 - share)).toInt() and BYTE) shl shift
            return BLACK or channel(RED) or channel(GREEN) or channel(0)
        }
    }
}
