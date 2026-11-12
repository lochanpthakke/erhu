package com.erhu.erhu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.component.GlassMenuItem
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.AnimatedArtOrientation
import com.erhu.erhu.core.model.AnimatedArtStatus
import com.erhu.erhu.feature.library.components.AnimatedAlbumCoverPlayer
import com.erhu.erhu.core.common.R as CommonR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedArtworkScreen(
    viewModel: AnimatedArtworkViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToLibrary: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchAlbums by viewModel.searchAlbums.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val isFetchingMaster by viewModel.isFetchingMaster.collectAsState()
    val isDownloadingFile by viewModel.isDownloadingFile.collectAsState()
    val availableVariants by viewModel.availableVariants.collectAsState()
    val selectedVariant by viewModel.selectedVariant.collectAsState()
    val previewStreamUrl by viewModel.previewStreamUrl.collectAsState()
    val queueItems by viewModel.queueItems.collectAsState()
    val orientation by viewModel.preferredOrientation.collectAsState()
    val selectedDiagnosticId by viewModel.selectedDiagnosticAlbumId.collectAsState()
    val successDialogArtwork by viewModel.successDialogArtwork.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var resolutionDropdownExpanded by remember { mutableStateOf(false) }

    val selectedDiagnosticItem = queueItems.find { it.album.id == selectedDiagnosticId }
    val locale = AppLocaleManager.activeLocale(LocalContext.current)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CommonR.string.anim_art_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = stringResource(CommonR.string.cd_open_drawer))
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Target Aspect Ratio Switcher
            Text(
                text = stringResource(CommonR.string.anim_art_target_ratio),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = orientation == AnimatedArtOrientation.SQUARE_1_1,
                    onClick = { viewModel.setOrientation(AnimatedArtOrientation.SQUARE_1_1) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(CommonR.string.anim_art_ratio_square))
                }
                SegmentedButton(
                    selected = orientation == AnimatedArtOrientation.TALL_3_4,
                    onClick = { viewModel.setOrientation(AnimatedArtOrientation.TALL_3_4) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text(stringResource(CommonR.string.anim_art_ratio_tall))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Local Album Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(CommonR.string.anim_art_search_albums)) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (searchQuery.isNotBlank()) {
                Text(
                    text = stringResource(CommonR.string.anim_art_search_results_hint),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                ) {
                    items(searchAlbums) { album ->
                        AlbumSearchCard(
                            album = album,
                            onAdd = {
                                viewModel.selectAlbumFromSearch(album)
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Selected Target Album Card
            selectedAlbum?.let { album ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model =
                                ImageRequest
                                    .Builder(LocalContext.current)
                                    .data(album.artworkUri)
                                    .crossfade(true)
                                    .build(),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(CommonR.string.anim_art_selected_album),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(album.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Fetch Motion Artwork Button (Step 1)
            Button(
                onClick = viewModel::triggerFetchMotionArtwork,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isFetchingMaster && !isDownloadingFile && selectedAlbum != null,
            ) {
                Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (isFetchingMaster) CommonR.string.anim_art_fetching_streams else CommonR.string.anim_art_fetch_motion,
                    ),
                )
            }

            if (isFetchingMaster) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Resolution Dropdown & Live HLS Stream Preview (Step 2)
            if (availableVariants.isNotEmpty() || previewStreamUrl != null) {
                Text(
                    text = stringResource(CommonR.string.anim_art_section_resolution_preview),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (availableVariants.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(CommonR.string.anim_art_select_stream_resolution),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )

                                Box {
                                    TextButton(onClick = { resolutionDropdownExpanded = true }) {
                                        val label =
                                            selectedVariant?.let {
                                                val formattedMb =
                                                    stringResource(
                                                        CommonR.string.format_size_mb,
                                                        LocaleFormatters.formatDecimal(
                                                            it.estimatedSizeMb.toDouble(),
                                                            decimals = 1,
                                                            locale = locale,
                                                        ),
                                                    )
                                                stringResource(CommonR.string.anim_art_resolution_with_size, it.resolutionText, formattedMb)
                                            } ?: stringResource(CommonR.string.anim_art_select_resolution)
                                        Text(label, fontWeight = FontWeight.Medium)
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                    GlassMenu(
                                        expanded = resolutionDropdownExpanded,
                                        onDismissRequest = { resolutionDropdownExpanded = false },
                                    ) {
                                        availableVariants.forEach { variant ->
                                            GlassMenuItem(
                                                text = {
                                                    val formattedMb =
                                                        stringResource(
                                                            CommonR.string.format_size_mb,
                                                            LocaleFormatters.formatDecimal(
                                                                variant.estimatedSizeMb.toDouble(),
                                                                decimals = 1,
                                                                locale = locale,
                                                            ),
                                                        )
                                                    Text(
                                                        stringResource(
                                                            CommonR.string.anim_art_variant_with_size,
                                                            variant.resolutionText,
                                                            formattedMb,
                                                        ),
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.selectVariant(variant)
                                                    resolutionDropdownExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        val aspectRatio = if (orientation == AnimatedArtOrientation.SQUARE_1_1) 1f else 3f / 4f

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspectRatio)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedAlbumCoverPlayer(
                                filePath = previewStreamUrl,
                                isPlaying = true,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                selectedAlbum?.let { album ->
                                    AsyncImage(
                                        model =
                                            ImageRequest
                                                .Builder(LocalContext.current)
                                                .data(album.artworkUri)
                                                .crossfade(true)
                                                .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = viewModel::downloadAndSaveArtwork,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isDownloadingFile && selectedVariant != null,
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (isDownloadingFile) {
                                        CommonR.string.anim_art_downloading_saving
                                    } else {
                                        CommonR.string.anim_art_download_save
                                    },
                                ),
                            )
                        }

                        if (isDownloadingFile) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Processing Queue List
            Text(
                text =
                    stringResource(
                        CommonR.string.anim_art_section_queue,
                        LocaleFormatters.formatInteger(queueItems.size, locale),
                    ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (queueItems.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Text(
                        text = stringResource(CommonR.string.anim_art_queue_empty),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                queueItems.forEach { item ->
                    AnimatedQueueItemCard(
                        item = item,
                        isSelected = item.album.id == selectedDiagnosticId,
                        onSelect = { viewModel.selectDiagnosticItem(item.album.id) },
                        onRemove = { viewModel.removeFromQueue(item.album.id) },
                        onRetry = { viewModel.retryItem(item.album) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Diagnostics / Preview Panel
            if (selectedDiagnosticItem != null) {
                Text(
                    text = stringResource(CommonR.string.anim_art_section_diagnostics),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = selectedDiagnosticItem.album.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = selectedDiagnosticItem.album.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val activeFilePath =
                            when (orientation) {
                                AnimatedArtOrientation.SQUARE_1_1 ->
                                    selectedDiagnosticItem.artwork?.squareFilePath
                                        ?: selectedDiagnosticItem.artwork?.tallFilePath
                                AnimatedArtOrientation.TALL_3_4 ->
                                    selectedDiagnosticItem.artwork?.tallFilePath
                                        ?: selectedDiagnosticItem.artwork?.squareFilePath
                            }

                        val aspectRatio = if (orientation == AnimatedArtOrientation.SQUARE_1_1) 1f else 3f / 4f

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspectRatio)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedAlbumCoverPlayer(
                                filePath = activeFilePath,
                                isPlaying = true,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                AsyncImage(
                                    model =
                                        ImageRequest
                                            .Builder(LocalContext.current)
                                            .data(selectedDiagnosticItem.album.artworkUri)
                                            .crossfade(true)
                                            .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val art = selectedDiagnosticItem.artwork
                        val naText = stringResource(CommonR.string.common_na)
                        Text(
                            stringResource(
                                CommonR.string.anim_art_diag_status,
                                art?.status?.localizedLabel() ?: stringResource(CommonR.string.common_unchecked),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            stringResource(CommonR.string.anim_art_diag_catalog_id, art?.catalogAlbumId ?: naText),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            stringResource(CommonR.string.anim_art_diag_storefront, art?.storefront ?: naText),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            stringResource(CommonR.string.anim_art_diag_has_square, art?.hasSquare.localizedYesNoUnknown()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            stringResource(CommonR.string.anim_art_diag_has_tall, art?.hasTall.localizedYesNoUnknown()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (activeFilePath != null) {
                            Text(
                                stringResource(CommonR.string.anim_art_diag_local_file, activeFilePath),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Error Message Alert
    errorMessage?.let { message ->
        GlassAlertDialog(
            onDismissRequest = viewModel::dismissErrorMessage,
            title = { Text(stringResource(CommonR.string.dialog_notice)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissErrorMessage) {
                    Text(stringResource(CommonR.string.action_ok))
                }
            },
        )
    }

    // Success Completion Dialog Popup
    successDialogArtwork?.let { artwork ->
        val filePath = artwork.squareFilePath ?: artwork.tallFilePath ?: stringResource(CommonR.string.anim_art_internal_storage)
        GlassAlertDialog(
            onDismissRequest = viewModel::dismissSuccessDialog,
            title = { Text(stringResource(CommonR.string.anim_art_success_title)) },
            text = {
                Column {
                    Text(stringResource(CommonR.string.anim_art_success_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(CommonR.string.anim_art_saved_to),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(filePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetSearchAndQueue()
                }) {
                    Text(stringResource(CommonR.string.artwork_fetch_more))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissSuccessDialog()
                    onNavigateToLibrary()
                }) {
                    Text(stringResource(CommonR.string.common_return_to_library))
                }
            },
        )
    }
}

@Composable
private fun Boolean?.localizedYesNoUnknown(): String =
    when (this) {
        true -> stringResource(CommonR.string.common_yes)
        false -> stringResource(CommonR.string.common_no)
        null -> stringResource(CommonR.string.common_unknown)
    }

@Composable
private fun AnimatedArtStatus.localizedLabel(): String =
    when (this) {
        AnimatedArtStatus.UNCHECKED -> stringResource(CommonR.string.anim_art_status_pending)
        AnimatedArtStatus.DOWNLOADING -> stringResource(CommonR.string.anim_art_status_downloading)
        AnimatedArtStatus.AVAILABLE -> stringResource(CommonR.string.anim_art_status_available)
        AnimatedArtStatus.FOUND -> stringResource(CommonR.string.anim_art_status_found)
        AnimatedArtStatus.NO_MATCH -> stringResource(CommonR.string.anim_art_status_no_match)
        AnimatedArtStatus.NO_ANIMATION -> stringResource(CommonR.string.anim_art_status_no_video)
        AnimatedArtStatus.FAILED -> stringResource(CommonR.string.anim_art_status_failed)
    }

@Composable
private fun AlbumSearchCard(
    album: Album,
    onAdd: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(album.artworkUri)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            IconButton(onClick = onAdd) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(CommonR.string.anim_art_cd_select_album))
            }
        }
    }
}

@Composable
private fun AnimatedQueueItemCard(
    item: AnimatedArtworkQueueItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    val artwork = item.artwork
    val status = artwork?.status ?: AnimatedArtStatus.UNCHECKED

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(item.album.artworkUri)
                            .crossfade(true)
                            .build(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(item.album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.album.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                StatusBadge(status = status)

                if (status == AnimatedArtStatus.FAILED ||
                    status == AnimatedArtStatus.NO_MATCH ||
                    status == AnimatedArtStatus.NO_ANIMATION
                ) {
                    IconButton(onClick = onRetry) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = stringResource(CommonR.string.action_retry))
                    }
                }

                IconButton(onClick = onRemove) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(CommonR.string.action_remove))
                }
            }

            if (status == AnimatedArtStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StatusBadge(status: AnimatedArtStatus) {
    val (icon, color, text) =
        when (status) {
            AnimatedArtStatus.UNCHECKED ->
                Triple(Icons.Default.MovieFilter, MaterialTheme.colorScheme.outline, status.localizedLabel())
            AnimatedArtStatus.DOWNLOADING ->
                Triple(Icons.Default.MovieFilter, MaterialTheme.colorScheme.primary, status.localizedLabel())
            AnimatedArtStatus.AVAILABLE ->
                Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, status.localizedLabel())
            AnimatedArtStatus.FOUND ->
                Triple(Icons.Default.MovieFilter, MaterialTheme.colorScheme.primary, status.localizedLabel())
            AnimatedArtStatus.NO_MATCH ->
                Triple(Icons.Default.Error, MaterialTheme.colorScheme.outline, status.localizedLabel())
            AnimatedArtStatus.NO_ANIMATION ->
                Triple(Icons.Default.Error, MaterialTheme.colorScheme.outline, status.localizedLabel())
            AnimatedArtStatus.FAILED ->
                Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, status.localizedLabel())
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
