package com.erhu.erhu.feature.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.erhu.erhu.core.common.artwork.ArtworkLoader
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.common.locale.localizedMessage
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.common.result.getOrNull
import com.erhu.erhu.core.database.library.ArtistArtworkCache
import com.erhu.erhu.core.database.repository.ArtistArtworkRepository
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.network.artwork.AlbumArtworkQuery
import com.erhu.erhu.core.network.artwork.ArtworkUpgradeImages
import com.erhu.erhu.core.network.artwork.ArtworkUpgradeMatch
import com.erhu.erhu.core.network.artwork.UpgradeImageFacts
import com.erhu.erhu.core.network.repository.MetadataRepository
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import com.erhu.erhu.core.common.R as CommonR

internal enum class ArtworkUpgradeKind { ALBUMS, ARTISTS }

internal data class ArtworkUpgradeTarget(
    val album: Album? = null,
    val artist: Artist? = null,
) {
    val id: String get() = album?.let { "album-${it.id}" } ?: "artist-${artist!!.id}"
    val name: String get() = album?.title ?: artist!!.name
    val subtitle: String get() = album?.artist ?: "Artist photo"
}

internal data class ArtworkUpgradeResult(
    val target: ArtworkUpgradeTarget,
    val original: UpgradeImageFacts,
    val replacement: UpgradeImageFacts,
    val file: File,
    val preview: File,
    val source: String,
)

internal data class ArtworkUpgradeState(
    val scanning: Boolean = false,
    val saving: Boolean = false,
    val checked: Int = 0,
    val total: Int = 0,
    val skipped: Int = 0,
    val finished: Boolean = false,
    val results: List<ArtworkUpgradeResult> = emptyList(),
    val message: String? = null,
)

/** Owned by LibraryViewModel: shares catalogues, artist storage and its Android write-consent flow. */
@Suppress("TooGenericExceptionCaught") // Per-target IO boundaries report failures without aborting the entire scan.
internal class ArtworkUpgradeController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val library: LibraryRepository,
    private val metadata: MetadataRepository,
    private val artists: ArtistArtworkRepository,
    private val writeAccess: suspend (List<String>, suspend () -> Unit, suspend () -> Unit) -> Unit,
) {
    private val mutableState = MutableStateFlow(ArtworkUpgradeState())
    val state = mutableState.asStateFlow()
    private var scan: Job? = null
    private var selectedKind: ArtworkUpgradeKind? = null
    private val directory = File(context.cacheDir, "artwork-upgrades/${UUID.randomUUID()}")

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion { directory.deleteRecursively() }
    }

    fun stop() {
        scan?.cancel()
    }

    suspend fun select(kind: ArtworkUpgradeKind) {
        if (selectedKind == kind || state.value.saving) return
        scan?.cancelAndJoin()
        directory.deleteRecursively()
        mutableState.value = ArtworkUpgradeState()
        selectedKind = kind
    }

    fun search(kind: ArtworkUpgradeKind) {
        if (state.value.scanning || state.value.saving) return
        selectedKind = kind
        directory.deleteRecursively()
        directory.mkdirs()
        mutableState.value = ArtworkUpgradeState(scanning = true)
        scan =
            scope.launch(Dispatchers.IO) {
                try {
                    val targets = targets(kind)
                    mutableState.update { it.copy(total = targets.size) }
                    for (target in targets) {
                        currentCoroutineContext().ensureActive()
                        try {
                            withTimeout(TARGET_TIMEOUT_MS) { find(target) }?.let { result ->
                                currentCoroutineContext().ensureActive()
                                mutableState.update { it.copy(results = it.results + result) }
                            }
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                            currentCoroutineContext().ensureActive()
                            mutableState.update { it.copy(skipped = it.skipped + 1) }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            mutableState.update { it.copy(skipped = it.skipped + 1) }
                        }
                        mutableState.update { it.copy(checked = it.checked + 1) }
                        if (cacheLimitReached()) {
                            mutableState.update {
                                it.copy(
                                    message = localized().getString(CommonR.string.artwork_upgrade_msg_cache_limit),
                                )
                            }
                            break
                        }
                        delay(SEARCH_PAUSE_MS)
                    }
                    mutableState.update { it.copy(finished = true) }
                } catch (error: CancellationException) {
                    mutableState.update { it.copy(message = localized().getString(CommonR.string.artwork_upgrade_msg_stopped)) }
                    throw error
                } catch (_: Exception) {
                    mutableState.update { it.copy(message = localized().getString(CommonR.string.artwork_upgrade_msg_library_error)) }
                } finally {
                    mutableState.update { it.copy(scanning = false) }
                }
            }
    }

    private suspend fun targets(kind: ArtworkUpgradeKind): List<ArtworkUpgradeTarget> =
        when (kind) {
            ArtworkUpgradeKind.ALBUMS -> library.getAllAlbums().first().map { ArtworkUpgradeTarget(album = it) }
            ArtworkUpgradeKind.ARTISTS ->
                library
                    .getAllArtists()
                    .first()
                    .filter { !it.isUnresolved && ArtistArtworkCache.hasArtwork(it.artworkUri) }
                    .map { ArtworkUpgradeTarget(artist = it) }
        }

    @Suppress("ReturnCount") // Guard clauses reject unusable originals before any network work.
    private suspend fun find(target: ArtworkUpgradeTarget): ArtworkUpgradeResult? {
        val originalBytes = readOriginal(target) ?: error("No readable existing image")
        val original = ArtworkUpgradeImages.inspect(originalBytes) ?: error("Unsupported existing image")
        if (maxOf(original.width, original.height) > ArtworkUpgradeMatch.MAX_SAVED_PIXELS / ArtworkUpgradeMatch.MIN_GAIN) return null
        for ((url, source) in candidates(target, original).distinctBy { it.first }.take(MAX_CANDIDATES)) {
            currentCoroutineContext().ensureActive()
            val bytes = matchingBytes(original, url, target.album == null) ?: continue
            val savedFacts = checkNotNull(ArtworkUpgradeImages.inspect(bytes))
            currentCoroutineContext().ensureActive()
            val file = File(directory, "${target.id}.img").apply { writeBytes(bytes) }
            val preview = File(directory, "${target.id}-original.img").apply { writeBytes(originalBytes) }
            return ArtworkUpgradeResult(target, original, savedFacts, file, preview, source)
        }
        return null
    }

    private suspend fun candidates(
        target: ArtworkUpgradeTarget,
        original: UpgradeImageFacts,
    ): List<Pair<String, String>> {
        val album = target.album
        if (album == null) {
            val result =
                metadata.fetchArtistArtworkCandidatesDetailed(target.artist!!.name).getOrNull()
                    ?: error("Artist search unavailable")
            val sourceLabel = localized().getString(CommonR.string.artwork_source_deezer_artist)
            return result.candidates.map { it to sourceLabel }
        }
        val result =
            metadata.searchAlbumArtwork(AlbumArtworkQuery(album.title, album.artist, album.year)).getOrNull()
                ?: error("Album search unavailable")
        check(result.candidates.isNotEmpty() || result.outcomes.none { it.status in FAILED_STATUSES }) { "Providers unavailable" }
        return result.candidates
            .filter {
                it.verifiedImage &&
                    ArtworkUpgradeMatch.isLarger(it.width, it.height, original.width, original.height)
            }.sortedByDescending { it.width.toLong() * it.height }
            .map { it.imageUrl to it.source }
    }

    @Suppress("ReturnCount") // Each decoding/geometry check is an independent rejection gate.
    private suspend fun matchingBytes(original: UpgradeImageFacts, url: String, preserveTransparency: Boolean): ByteArray? {
        val downloaded = metadata.downloadArtwork(url).getOrNull()
        val facts = downloaded?.let(ArtworkUpgradeImages::inspect)
        if (downloaded == null || facts == null) {
            mutableState.update { it.copy(skipped = it.skipped + 1) }
            return null
        }
        if (!matches(original, facts)) return null
        val bytes = ArtworkUpgradeImages.prepare(downloaded, preserveTransparency) ?: return null
        val savedFacts = ArtworkUpgradeImages.inspect(bytes) ?: return null
        return bytes.takeIf { matches(original, savedFacts) }
    }

    private fun matches(
        original: UpgradeImageFacts,
        candidate: UpgradeImageFacts,
    ): Boolean =
        ArtworkUpgradeMatch.isLarger(candidate.width, candidate.height, original.width, original.height) &&
            ArtworkUpgradeMatch.resembles(original.signature, candidate.signature)

    @Suppress("ReturnCount") // Album embedded bytes take precedence over a MediaStore thumbnail.
    private suspend fun readOriginal(target: ArtworkUpgradeTarget): ByteArray? {
        target.album?.let { album ->
            val first = library.getSongsByAlbumId(album.id).first().firstOrNull()
            first?.let { embedded(it) }?.let { return it }
            return readImage(album.artworkUri)
        }
        val artist = library.getArtistById(target.artist!!.id).first() ?: return null
        return readImage(artist.artworkUri)
    }

    private fun embedded(song: Song): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(song.uri))
            retriever.embeddedPicture?.takeIf { it.size <= ArtworkUpgradeImages.MAX_BYTES }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private suspend fun readImage(value: String?): ByteArray? {
        if (value.isNullOrBlank()) return null
        val uri = Uri.parse(value)
        return if (uri.scheme in listOf("http", "https")) {
            metadata.downloadArtwork(value).getOrNull()
        } else {
            context.contentResolver.openInputStream(uri)?.use(::readBoundedImage)
        }
    }

    private fun readBoundedImage(input: java.io.InputStream): ByteArray =
        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = input.read(buffer)
            while (count != -1) {
                check(output.size() + count <= ArtworkUpgradeImages.MAX_BYTES) { "Existing image is too large" }
                output.write(buffer, 0, count)
                count = input.read(buffer)
            }
            output.toByteArray()
        }

    fun apply(result: ArtworkUpgradeResult) {
        if (state.value.saving || result !in state.value.results) return
        mutableState.update { it.copy(saving = true, message = null) }
        scope.launch(Dispatchers.IO) {
            try {
                checkedBytes(result)
                if (result.target.album != null) {
                    val songs = library.getSongsByAlbumId(result.target.album.id).first()
                    require(songs.isNotEmpty()) { localized().getString(CommonR.string.artwork_upgrade_msg_album_missing) }
                    writeAccess(songs.map { it.uri }, {
                        withContext(Dispatchers.IO) { saveAlbum(result, songs) }
                    }, { complete(localized().getString(CommonR.string.artwork_upgrade_msg_permission_denied)) })
                } else {
                    val bytes = checkedBytes(result)
                    val localizedContext = localized()
                    when (val outcome = artists.setCustomArtwork(result.target.artist!!.id, bytes)) {
                        is AppResult.Success ->
                            saved(result, localizedContext.getString(CommonR.string.artwork_upgrade_msg_artist_saved))
                        is AppResult.Error -> complete(outcome.error.localizedMessage(localizedContext))
                    }
                }
            } catch (error: CancellationException) {
                complete(localized().getString(CommonR.string.artwork_upgrade_msg_saving_cancelled))
                throw error
            } catch (error: Exception) {
                complete(error.message ?: localized().getString(CommonR.string.artwork_upgrade_msg_save_failed))
            }
        }
    }

    private suspend fun checkedBytes(result: ArtworkUpgradeResult): ByteArray {
        val current = readOriginal(result.target)?.let(ArtworkUpgradeImages::inspect)
        check(current?.hash == result.original.hash) { localized().getString(CommonR.string.artwork_upgrade_msg_original_changed) }
        val bytes = result.file.readBytes()
        val facts = ArtworkUpgradeImages.inspect(bytes)
        check(facts != null && facts.hash == result.replacement.hash && matches(result.original, facts)) {
            localized().getString(CommonR.string.artwork_upgrade_msg_preview_expired)
        }
        return bytes
    }

    private suspend fun saveAlbum(
        result: ArtworkUpgradeResult,
        songs: List<Song>,
    ) {
        try {
            val bytes = checkedBytes(result)
            var written = 0
            for (song in songs) {
                val facts = embedded(song)?.let(ArtworkUpgradeImages::inspect)
                if (facts == null || !matches(facts, result.replacement)) continue
                when (
                    metadata.writeTagsToFile(
                        TagWriteRequest(song.uri, song.filePath ?: library.getSongFilePath(song.id).orEmpty(), artworkBytes = bytes),
                    )
                ) {
                    is AppResult.Success -> {
                        written++
                        ArtworkLoader.invalidate(song.uri)
                    }
                    is AppResult.Error -> Unit
                }
            }
            result.target.album
                ?.artworkUri
                ?.let(ArtworkLoader::invalidate)
            val localizedContext = localized()
            val locale = AppLocaleManager.activeLocale(localizedContext)
            if (written > 0) {
                library.requestRescanAfterChange()
                saved(
                    result,
                    localizedContext.getString(
                        CommonR.string.artwork_upgrade_msg_album_saved,
                        LocaleFormatters.formatInteger(written, locale),
                        LocaleFormatters.formatInteger(songs.size, locale),
                    ),
                )
            } else {
                complete(localizedContext.getString(CommonR.string.artwork_upgrade_msg_none_upgraded))
            }
        } catch (error: CancellationException) {
            complete(localized().getString(CommonR.string.artwork_upgrade_msg_saving_cancelled))
            throw error
        } catch (error: Exception) {
            complete(error.message ?: localized().getString(CommonR.string.artwork_upgrade_msg_cover_save_failed))
        }
    }

    private fun localized(): Context = AppLocaleManager.localizedContext(context)

    private fun saved(
        result: ArtworkUpgradeResult,
        message: String,
    ) {
        result.file.delete()
        result.preview.delete()
        mutableState.update { it.copy(results = it.results - result, saving = false, message = message) }
    }

    private fun complete(message: String) {
        mutableState.update { it.copy(saving = false, message = message) }
    }

    private fun cacheLimitReached(): Boolean =
        state.value.results.size >= MAX_RESULTS || directory.listFiles().orEmpty().sumOf { it.length() } >= MAX_CACHE_BYTES

    private companion object {
        const val TARGET_TIMEOUT_MS = 60000L
        const val SEARCH_PAUSE_MS = 300L
        const val MAX_CANDIDATES = 12
        const val MAX_RESULTS = 100
        const val MAX_CACHE_BYTES = 64L * 1024 * 1024
        val FAILED_STATUSES =
            setOf(
                com.erhu.erhu.core.model.ProviderStatus.FAILED,
                com.erhu.erhu.core.model.ProviderStatus.RATE_LIMITED,
                com.erhu.erhu.core.model.ProviderStatus.TIMED_OUT,
            )
    }
}
