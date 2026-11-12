@file:Suppress("detekt:All")

package com.erhu.erhu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.ActionButton
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.component.GroupDivider
import com.erhu.erhu.core.designsystem.component.GroupRow
import com.erhu.erhu.core.designsystem.component.LocalCollapsingTitle
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.ProgressBar
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.component.SearchField
import com.erhu.erhu.core.designsystem.component.SectionHeading
import com.erhu.erhu.core.designsystem.component.SegmentedSelector
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.groupSurface
import com.erhu.erhu.core.designsystem.component.rememberCollapsingTitleState
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.common.R as CommonR

/** The resolutions offered as the minimum artwork size, px. */
private val RESOLUTION_TARGETS = listOf(300, 500, 1000)

/** Leading artwork placeholder of a queue or search row. */
private val ROW_THUMB_SIZE = 40.dp

/** Tint behind a row's placeholder icon, in the theme accent. */
private const val ROW_THUMB_ALPHA = 0.14f

/** The candidates' size badge: a dense backing so it stays readable over any artwork. */
private const val BADGE_FILL_ALPHA = 0.85f

private const val USER_AGENT = "erhu/1.0.0 (https://github.com/lochanpthakke/empee3)"

/**
 * Artwork Fetcher: find missing album covers and artist photos and apply chosen artwork. The page
 * is grouped like Settings (accent section headings over tinted cards) under the app's floating
 * header, with the queue's search as its one prominent action.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
fun ArtworkManagerScreen(
    viewModel: LibraryViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToMetadataSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artworkQueue by viewModel.artworkQueue.collectAsState()
    val artistArtworkQueue by viewModel.artistArtworkQueue.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(if (artworkQueue.isEmpty() && artistArtworkQueue.isNotEmpty()) 1 else 0) }
    var upgradeTab by rememberSaveable { mutableStateOf<Int?>(null) }
    if (upgradeTab != null) {
        ArtworkUpgradeScreen(
            controller = viewModel.artworkUpgrades,
            kind = if (upgradeTab == 0) ArtworkUpgradeKind.ALBUMS else ArtworkUpgradeKind.ARTISTS,
            onBack = { upgradeTab = null },
            modifier = modifier,
        )
        return
    }
    var searchQuery by remember { mutableStateOf("") }
    var appliedItemSuccessTitle by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            viewModel.clearArtworkDiagnostics()
        }
    }

    val searchAlbums by viewModel.searchAlbums.collectAsState()
    val searchArtists by viewModel.searchArtists.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isSearchingArtwork by viewModel.isSearchingArtwork.collectAsState()
    val artworkCandidates by viewModel.artworkCandidates.collectAsState()
    val currentProcessingAlbum by viewModel.currentProcessingAlbum.collectAsState()
    val currentProcessingArtist by viewModel.currentProcessingArtist.collectAsState()
    val selectedEnlargedArtworkUrl by viewModel.selectedEnlargedArtworkUrl.collectAsState()
    val minResolution by viewModel.minArtworkResolution.collectAsState()

    val activeQueueCount = if (selectedTab == 0) artworkQueue.size else artistArtworkQueue.size
    val doneCount =
        if (selectedTab == 0) {
            artworkQueue.count { it.status == "COMPLETED" || it.status == "DONE" || it.status == "SUCCESS" }
        } else {
            artistArtworkQueue.count { it.status == "COMPLETED" || it.status == "DONE" || it.status == "SUCCESS" }
        }
    val isQueueComplete =
        activeQueueCount > 0 &&
            if (selectedTab == 0) {
                artworkQueue.none { it.status == "PENDING" || it.status == "SEARCHING" || it.status == "WRITING" }
            } else {
                artistArtworkQueue.none { it.status == "PENDING" || it.status == "SEARCHING" || it.status == "WRITING" }
            }
    val progressFloat = if (activeQueueCount > 0) doneCount.toFloat() / activeQueueCount.toFloat() else 0f

    val locale = AppLocaleManager.activeLocale(LocalContext.current)

    // The page title merges into the header button as the page scrolls.
    val collapsingTitle = rememberCollapsingTitleState()
    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.TopLevel(onOpenDrawer),
                title = stringResource(CommonR.string.artwork_fetcher_title),
                collapsingTitle = collapsingTitle,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent(topOverlay = true)
        val scrollState = rememberScrollState()
        CompositionLocalProvider(LocalCollapsingTitle provides collapsingTitle) { ReportTitleScroll(scrollState) }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scroll.container)
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.screenGutter)
                    .padding(top = scroll.contentTop + Spacing.sm, bottom = scroll.contentBottom + Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            SegmentedSelector(
                options =
                    listOf(
                        stringResource(CommonR.string.artwork_tab_albums),
                        stringResource(CommonR.string.artwork_tab_artists),
                    ),
                selectedIndex = selectedTab,
                onSelect = { index ->
                    if (selectedTab != index) {
                        selectedTab = index
                        searchQuery = ""
                        viewModel.clearArtworkDiagnostics()
                    }
                },
            )

            // ---- Find: where artwork comes from, and what to fetch it for.
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionHeading(title = stringResource(CommonR.string.artwork_section_find))
                Column(modifier = Modifier.fillMaxWidth().groupSurface()) {
                    Text(
                        if (selectedTab == 0) {
                            stringResource(CommonR.string.artwork_source_album_chain)
                        } else {
                            stringResource(CommonR.string.artwork_source_deezer_artist)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(Spacing.lg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GroupDivider()
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        SearchField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchLocalLibrary(it)
                            },
                            placeholder =
                                stringResource(
                                    if (selectedTab == 0) {
                                        CommonR.string.artwork_search_albums_hint
                                    } else {
                                        CommonR.string.artwork_search_artists_hint
                                    },
                                ),
                        )

                        if (searchQuery.isNotBlank()) {
                            Text(
                                text = stringResource(CommonR.string.artwork_search_results_hint),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val resultsEmpty = if (selectedTab == 0) searchAlbums.isEmpty() else searchArtists.isEmpty()
                            if (resultsEmpty) {
                                Text(
                                    text =
                                        stringResource(
                                            if (selectedTab == 0) {
                                                CommonR.string.artwork_no_matching_albums
                                            } else {
                                                CommonR.string.artwork_no_matching_artists
                                            },
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                                    if (selectedTab == 0) {
                                        items(searchAlbums) { album ->
                                            AlbumSearchCard(
                                                album = album,
                                                onAdd = {
                                                    viewModel.enqueueAlbumForArtwork(album)
                                                    searchQuery = ""
                                                    viewModel.searchLocalLibrary("")
                                                },
                                            )
                                        }
                                    } else {
                                        items(searchArtists) { artist ->
                                            ArtistSearchCard(
                                                artist = artist,
                                                onAdd = {
                                                    viewModel.enqueueArtistForArtwork(artist)
                                                    searchQuery = ""
                                                    viewModel.searchLocalLibrary("")
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ActionButton(
                            text =
                                stringResource(
                                    when {
                                        isScanning -> CommonR.string.artwork_scanning_storage
                                        selectedTab == 0 -> CommonR.string.artwork_scan_missing_covers
                                        else -> CommonR.string.artwork_scan_missing_photos
                                    },
                                ),
                            onClick = {
                                if (selectedTab == 0) {
                                    viewModel.scanForMissingCovers()
                                } else {
                                    viewModel.scanForMissingArtistPhotos()
                                }
                            },
                            icon = Icons.Default.AutoFixHigh,
                            enabled = !isScanning && !isSearchingArtwork,
                        )
                    }
                }
            }

            // ---- Resolution target.
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionHeading(title = stringResource(CommonR.string.artwork_section_improve))
                Column(modifier = Modifier.fillMaxWidth().groupSurface()) {
                    GroupRow(
                        title =
                            stringResource(
                                if (selectedTab == 0) CommonR.string.artwork_upgrade_covers else CommonR.string.artwork_upgrade_photos,
                            ),
                        summary = stringResource(CommonR.string.artwork_upgrade_summary),
                        onClick = { upgradeTab = selectedTab },
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                val minResFormatted = LocaleFormatters.formatInteger(minResolution, locale)
                SectionHeading(
                    title = stringResource(CommonR.string.artwork_section_resolution_target),
                    trailing = stringResource(CommonR.string.artwork_resolution_target_trailing, minResFormatted, minResFormatted),
                )
                SegmentedSelector(
                    options =
                        RESOLUTION_TARGETS.map {
                            stringResource(CommonR.string.artwork_resolution_chip, LocaleFormatters.formatInteger(it, locale))
                        },
                    selectedIndex = RESOLUTION_TARGETS.indexOf(minResolution),
                    onSelect = { index ->
                        val res = RESOLUTION_TARGETS[index]
                        if (minResolution != res) viewModel.setMinArtworkResolution(res)
                    },
                )
            }

            // ---- The item being fetched: provider diagnostics and the candidates found.
            val currentContextTitle = if (selectedTab == 0) currentProcessingAlbum?.title else currentProcessingArtist?.name
            val currentContextSub =
                if (selectedTab == 0) {
                    currentProcessingAlbum?.artist.orEmpty()
                } else {
                    val songCount = currentProcessingArtist?.songCount ?: 0
                    pluralStringResource(
                        CommonR.plurals.library_tracks_count,
                        songCount,
                        LocaleFormatters.formatInteger(songCount, locale),
                    )
                }
            var showDiagnosticsExpanded by remember { mutableStateOf(false) }
            val diagnostics by viewModel.artworkSearchDiagnostics.collectAsState()

            if (currentContextTitle != null) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeading(title = stringResource(CommonR.string.artwork_section_candidates))
                    ArtworkWebSearchButton(
                        url =
                            if (selectedTab == 0) {
                                currentProcessingAlbum?.let { albumArtworkSearchUrl(it.title, it.artist) }
                            } else {
                                currentProcessingArtist?.let { artistArtworkSearchUrl(it.name) }
                            },
                        targetName = currentContextTitle,
                    )

                    if (diagnostics != null) {
                        Column(modifier = Modifier.fillMaxWidth().groupSurface()) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Button) { showDiagnosticsExpanded = !showDiagnosticsExpanded }
                                        .padding(Spacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(CommonR.string.artwork_section_provider_diagnostics),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (diagnostics?.searchQuery.orEmpty().isNotBlank()) {
                                        Text(
                                            text =
                                                stringResource(
                                                    CommonR.string.artwork_search_query_label,
                                                    diagnostics!!.searchQuery,
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (diagnostics?.resolvedArtistInfo.orEmpty().isNotBlank()) {
                                        Text(
                                            text = diagnostics!!.resolvedArtistInfo!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (showDiagnosticsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription =
                                        stringResource(
                                            if (showDiagnosticsExpanded) {
                                                CommonR.string.artwork_cd_hide_provider_results
                                            } else {
                                                CommonR.string.artwork_cd_show_provider_results
                                            },
                                        ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (showDiagnosticsExpanded) {
                                GroupDivider()
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                ) {
                                    Text(
                                        stringResource(CommonR.string.artwork_provider_results),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    diagnostics!!.providerOutcomes.forEach { outcome ->
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                            Text(
                                                text = outcome.providerName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f).padding(end = Spacing.sm),
                                            )
                                            Text(
                                                text =
                                                    when (outcome.status) {
                                                        ProviderStatus.SUCCESS ->
                                                            pluralStringResource(
                                                                CommonR.plurals.artwork_provider_found_candidates,
                                                                outcome.candidateCount,
                                                                LocaleFormatters.formatInteger(outcome.candidateCount, locale),
                                                            )
                                                        else -> outcome.status.localizedLabel()
                                                    },
                                                style = MaterialTheme.typography.bodySmall,
                                                color =
                                                    if (outcome.status == ProviderStatus.SUCCESS) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Column {
                            Text(
                                text = currentContextTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (currentContextSub.isNotBlank()) {
                                Text(
                                    text = currentContextSub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (isSearchingArtwork) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                ProgressBar()
                                Text(
                                    stringResource(CommonR.string.artwork_searching),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (artworkCandidates.isNotEmpty()) {
                            Text(
                                stringResource(CommonR.string.artwork_tap_candidate_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.heightIn(max = 320.dp),
                                contentPadding = PaddingValues(0.dp),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                items(artworkCandidates) { candidateUrl ->
                                    SquareArtworkCandidateCard(
                                        imageUrl = candidateUrl,
                                        detail = diagnostics?.candidates?.firstOrNull { it.url == candidateUrl },
                                        selected =
                                            selectedEnlargedArtworkUrl == candidateUrl ||
                                                (
                                                    selectedEnlargedArtworkUrl == null &&
                                                        diagnostics?.candidates?.any { it.url == candidateUrl && it.recommended } == true
                                                ),
                                        onClick = { viewModel.selectEnlargedArtwork(candidateUrl) },
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(CommonR.string.artwork_no_candidates),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ---- Queue: progress, the page's primary action, and the queued items.
            Column(modifier = Modifier.fillMaxWidth()) {
                val doneFormatted = LocaleFormatters.formatInteger(doneCount, locale)
                val totalFormatted = LocaleFormatters.formatInteger(activeQueueCount, locale)
                SectionHeading(
                    title =
                        stringResource(
                            if (selectedTab == 0) {
                                CommonR.string.artwork_queue_covers_title
                            } else {
                                CommonR.string.artwork_queue_photos_title
                            },
                        ),
                    trailing =
                        pluralStringResource(
                            if (selectedTab == 0) {
                                CommonR.plurals.artwork_queue_albums_count
                            } else {
                                CommonR.plurals.artwork_queue_artists_count
                            },
                            activeQueueCount,
                            doneFormatted,
                            totalFormatted,
                        ),
                )
                Column(modifier = Modifier.fillMaxWidth().groupSurface()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        ProgressBar(progress = { progressFloat })

                        if (isQueueComplete) {
                            ActionButton(
                                text = stringResource(CommonR.string.artwork_finish_return),
                                onClick = {
                                    if (selectedTab == 0) viewModel.clearArtworkQueue() else viewModel.clearArtistArtworkQueue()
                                    onNavigateToLibrary()
                                },
                                icon = Icons.Default.CheckCircle,
                                primary = true,
                            )
                        } else {
                            ActionButton(
                                text =
                                    stringResource(
                                        if (isSearchingArtwork) {
                                            CommonR.string.artwork_searching_button
                                        } else {
                                            CommonR.string.artwork_search_button
                                        },
                                    ),
                                onClick = {
                                    if (selectedTab == 0) {
                                        val firstPending = artworkQueue.firstOrNull { it.status == "PENDING" }
                                        if (firstPending != null) viewModel.searchArtworkForAlbum(firstPending.album)
                                    } else {
                                        val firstPending = artistArtworkQueue.firstOrNull { it.status == "PENDING" }
                                        if (firstPending != null) viewModel.searchArtworkForArtist(firstPending.artist)
                                    }
                                },
                                icon = Icons.Default.ImageSearch,
                                primary = true,
                                enabled =
                                    !isSearchingArtwork &&
                                        if (selectedTab == 0) {
                                            artworkQueue.any { it.status == "PENDING" }
                                        } else {
                                            artistArtworkQueue.any { it.status == "PENDING" }
                                        },
                            )
                        }
                    }

                    GroupDivider()

                    if (selectedTab == 0) {
                        if (artworkQueue.isEmpty()) {
                            QueueEmpty(stringResource(CommonR.string.artwork_queue_empty_album))
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(artworkQueue, key = { it.album.id }) { queueItem ->
                                    AlbumQueueCard(
                                        title = queueItem.album.title,
                                        subtitle = queueItem.album.artist,
                                        status = queueItem.status,
                                        icon = Icons.Default.MusicNote,
                                        onRemove = { viewModel.removeFromArtworkQueue(queueItem.album) },
                                        webSearchUrl = albumArtworkSearchUrl(queueItem.album.title, queueItem.album.artist),
                                    )
                                }
                            }
                        }
                    } else {
                        if (artistArtworkQueue.isEmpty()) {
                            QueueEmpty(stringResource(CommonR.string.artwork_queue_empty_artist))
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(artistArtworkQueue, key = { it.artist.id }) { queueItem ->
                                    AlbumQueueCard(
                                        title = queueItem.artist.name,
                                        subtitle =
                                            pluralStringResource(
                                                CommonR.plurals.library_tracks_count,
                                                queueItem.artist.songCount,
                                                LocaleFormatters.formatInteger(queueItem.artist.songCount, locale),
                                            ),
                                        status = queueItem.status,
                                        icon = Icons.Default.Person,
                                        onRemove = { viewModel.removeFromArtistArtworkQueue(queueItem.artist) },
                                        webSearchUrl = artistArtworkSearchUrl(queueItem.artist.name),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- More: a quiet row, not a competing button.
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionHeading(title = stringResource(CommonR.string.artwork_more_section))
                Column(modifier = Modifier.fillMaxWidth().groupSurface()) {
                    GroupRow(
                        icon = Icons.Default.Settings,
                        title = stringResource(CommonR.string.artwork_utility_settings_title),
                        summary = stringResource(CommonR.string.artwork_utility_settings_summary),
                        onClick = onNavigateToMetadataSettings,
                        trailing = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    )
                }
            }
        }
    }

    if (selectedEnlargedArtworkUrl != null && (currentProcessingAlbum != null || currentProcessingArtist != null)) {
        val targetTitle = currentProcessingAlbum?.title ?: currentProcessingArtist?.name ?: ""
        GlassAlertDialog(
            onDismissRequest = { viewModel.selectEnlargedArtwork(null) },
            title = { Text(stringResource(CommonR.string.artwork_inspect_title, targetTitle)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(selectedEnlargedArtworkUrl?.let(viewModel::artworkPreviewUrl))
                                .setHeader("User-Agent", USER_AGENT)
                                .crossfade(true)
                                .build(),
                        contentDescription = stringResource(CommonR.string.artwork_cd_enlarged),
                        contentScale = ContentScale.Fit,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.medium),
                    )
                    val inspected =
                        viewModel.artworkSearchDiagnostics.collectAsState().value?.candidates?.firstOrNull {
                            it.url ==
                                selectedEnlargedArtworkUrl
                        }
                    inspected?.let { candidate ->
                        Text(
                            listOf(candidate.matchedAlbum, candidate.matchedArtist).filter(String::isNotBlank).joinToString(" \u00b7 "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            listOf(
                                candidate.sourceProvider,
                                candidate.edition,
                                if (candidate.width > 0) {
                                    "${LocaleFormatters.formatInteger(candidate.width, locale)}\u00d7" +
                                        LocaleFormatters.formatInteger(candidate.height, locale)
                                } else {
                                    ""
                                },
                            ).filter(String::isNotBlank).joinToString(" \u00b7 "),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text =
                            if (currentProcessingAlbum != null) {
                                stringResource(CommonR.string.artwork_confirm_album, targetTitle)
                            } else {
                                stringResource(CommonR.string.artwork_confirm_artist, targetTitle)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val albumToApply = currentProcessingAlbum
                        val artistToApply = currentProcessingArtist
                        val urlToApply = selectedEnlargedArtworkUrl
                        val title = albumToApply?.title ?: artistToApply?.name ?: ""

                        if (albumToApply != null && urlToApply != null) {
                            viewModel.applyArtworkToAlbum(albumToApply, urlToApply)
                        } else if (artistToApply != null && urlToApply != null) {
                            viewModel.applyArtistArtwork(artistToApply, urlToApply)
                        }

                        viewModel.selectEnlargedArtwork(null)
                        appliedItemSuccessTitle = title
                    },
                ) {
                    Text(stringResource(CommonR.string.artwork_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.selectEnlargedArtwork(null) }) {
                    Text(stringResource(CommonR.string.action_cancel))
                }
            },
        )
    }

    if (appliedItemSuccessTitle != null) {
        val appliedTitle = appliedItemSuccessTitle.orEmpty()
        GlassAlertDialog(
            onDismissRequest = { appliedItemSuccessTitle = null },
            title = { Text(stringResource(CommonR.string.artwork_applied_title)) },
            text = {
                Text(
                    text = stringResource(CommonR.string.artwork_applied_body, appliedTitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        appliedItemSuccessTitle = null
                    },
                ) {
                    Text(stringResource(CommonR.string.artwork_fetch_more))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        appliedItemSuccessTitle = null
                        if (selectedTab == 0) viewModel.clearArtworkQueue() else viewModel.clearArtistArtworkQueue()
                        onNavigateToLibrary()
                    },
                ) {
                    Text(stringResource(CommonR.string.common_return_to_library))
                }
            },
        )
    }
}

@Composable
private fun ColumnScope.QueueEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
    )
}

/** Placeholder artwork of a row: the item's kind on a soft accent tile. */
@Composable
private fun RowThumb(
    icon: ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
) {
    Box(
        modifier =
            Modifier
                .size(ROW_THUMB_SIZE)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = ROW_THUMB_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SquareArtworkCandidateCard(
    imageUrl: String,
    detail: ArtworkCandidateDetail?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        if (selected) 2.dp else 0.dp,
                        if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        MaterialTheme.shapes.small,
                    ).clickable(role = Role.Button, onClick = onClick),
        ) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(detail?.previewUrl ?: imageUrl)
                        .setHeader("User-Agent", USER_AGENT)
                        .crossfade(true)
                        .build(),
                contentDescription = stringResource(CommonR.string.artwork_cd_candidate),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.xs)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = BADGE_FILL_ALPHA))
                        .padding(horizontal = Spacing.sm, vertical = 2.dp),
            ) {
                Text(
                    text =
                        if (detail != null && detail.width > 0) {
                            "${LocaleFormatters.formatInteger(detail.width, locale)}\u00d7" +
                                LocaleFormatters.formatInteger(detail.height, locale)
                        } else {
                            stringResource(CommonR.string.artwork_preview_badge)
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        detail?.let { candidate ->
            Text(candidate.sourceProvider, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (candidate.matchedAlbum.isNotBlank()) Text(candidate.matchedAlbum, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            if (candidate.matchedArtist.isNotBlank()) {
                Text(
                    candidate.matchedArtist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
            val edition =
                listOfNotNull(
                    candidate.year.takeIf { it > 0 }?.let { LocaleFormatters.formatInteger(it, locale) },
                    candidate.edition.takeIf(String::isNotBlank),
                ).joinToString(" · ")
            if (edition.isNotBlank()) Text(edition, style = MaterialTheme.typography.labelSmall)
            Text(
                if (candidate.recommended) {
                    stringResource(CommonR.string.artwork_recommended_match)
                } else {
                    candidate.reviewReasons.firstOrNull() ?: stringResource(CommonR.string.artwork_choose_manually)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlbumQueueCard(
    title: String,
    subtitle: String,
    status: String,
    icon: ImageVector,
    onRemove: () -> Unit,
    webSearchUrl: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowThumb(icon = icon, shape = MaterialTheme.shapes.small)
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            when (status) {
                "SEARCHING", "WRITING" ->
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = stringResource(CommonR.string.artwork_status_searching),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                "COMPLETED", "DONE", "SUCCESS" ->
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(CommonR.string.artwork_status_done),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                "FAILED" ->
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = stringResource(CommonR.string.artwork_status_failed),
                        tint = MaterialTheme.colorScheme.error,
                    )
                else ->
                    Text(
                        text = stringResource(CommonR.string.artwork_status_pending),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(CommonR.string.artwork_cd_remove_queue),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ArtworkWebSearchButton(webSearchUrl, title, Modifier.padding(start = Spacing.lg))
        if (status == "SEARCHING" || status == "WRITING") {
            ProgressBar(modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm))
        }
    }
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    thumbShape: androidx.compose.ui.graphics.Shape,
    addDescription: String,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowThumb(icon = icon, shape = thumbShape)
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onAdd) {
            Icon(imageVector = Icons.Default.Add, contentDescription = addDescription, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AlbumSearchCard(
    album: Album,
    onAdd: () -> Unit,
) {
    SearchResultRow(
        title = album.title,
        subtitle = album.artist,
        icon = Icons.Default.MusicNote,
        thumbShape = MaterialTheme.shapes.small,
        addDescription = stringResource(CommonR.string.artwork_cd_add_album),
        onAdd = onAdd,
    )
}

@Composable
private fun ArtistSearchCard(
    artist: Artist,
    onAdd: () -> Unit,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    SearchResultRow(
        title = artist.name,
        subtitle =
            pluralStringResource(
                CommonR.plurals.library_tracks_count,
                artist.songCount,
                LocaleFormatters.formatInteger(artist.songCount, locale),
            ),
        icon = Icons.Default.Person,
        thumbShape = CircleShape,
        addDescription = stringResource(CommonR.string.artwork_cd_add_artist),
        onAdd = onAdd,
    )
}
