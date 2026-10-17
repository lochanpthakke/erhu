package com.lochanpthakke.empee3.feature.library.vybe

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.common.perf.Interactions
import com.lochanpthakke.empee3.core.common.perf.traced
import com.lochanpthakke.empee3.core.database.repository.LibraryRepository
import com.lochanpthakke.empee3.core.vybe.EmbeddingRole
import com.lochanpthakke.empee3.core.vybe.IndexedSong
import com.lochanpthakke.empee3.core.vybe.SongText
import com.lochanpthakke.empee3.core.vybe.SongVectors
import com.lochanpthakke.empee3.core.vybe.VybeAnswer
import com.lochanpthakke.empee3.core.vybe.VybeDiscovery
import com.lochanpthakke.empee3.core.vybe.VybeIndexFile
import com.lochanpthakke.empee3.core.vybe.VybeMatch
import com.lochanpthakke.empee3.core.vybe.VybeText
import com.lochanpthakke.empee3.core.vybe.mediapipe.MediaPipeTextEmbedder
import com.lochanpthakke.empee3.core.vybe.mediapipe.VybeEmbedderException
import com.lochanpthakke.empee3.core.vybe.textHash
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Where VYBE stands. */
sealed interface VybeStatus {
    /** The model file hasn't been supplied (see [VybeEngine.importModel]). */
    data object NoModel : VybeStatus

    data object LoadingModel : VybeStatus

    /** Songs are being described and embedded on the device; search works on the [done] ones. */
    data class Indexing(
        val done: Int,
        val total: Int,
    ) : VybeStatus

    data class Ready(
        val songs: Int,
    ) : VybeStatus

    data class Failed(
        val message: String,
    ) : VybeStatus
}

/** Model load and per-embedding timings measured on this device, for the screen's details line. */
data class VybeTimings(
    val modelLoadMs: Long? = null,
    val averageEmbedMs: Long? = null,
    val lastQueryMs: Long? = null,
)

/**
 * VYBE on the device: loads EmbeddingGemma (a file the user supplied, kept in app storage),
 * keeps a vector index of the library's song descriptions up to date, and answers queries.
 *
 * - Everything model- and index-related runs on one background thread, in order: MediaPipe's
 *   embedder isn't shared across threads, and the index file has a single writer.
 * - Indexing embeds only songs whose description changed (or is new), each distinct description
 *   once, appending to the index as it goes: an interrupted run resumes where it stopped.
 *   Library changes (a rescan, new genres or BPM) re-trigger it, debounced.
 * - The model takes ~230 MB once loaded (measured on the S23): it is released a minute after VYBE
 *   is left, unless indexing is still running, and loaded again (~1-2 s) on the next search.
 * - Nothing leaves the phone: no network is used anywhere here.
 */
@Singleton
class VybeEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val libraryRepository: LibraryRepository,
        private val logger: Logger,
    ) {
        private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "vybe").apply { isDaemon = true } }.asCoroutineDispatcher()
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)

        private val _status = MutableStateFlow<VybeStatus>(if (modelFile().isFile) VybeStatus.LoadingModel else VybeStatus.NoModel)
        val status: StateFlow<VybeStatus> = _status.asStateFlow()

        private val _timings = MutableStateFlow(VybeTimings())
        val timings: StateFlow<VybeTimings> = _timings.asStateFlow()

        private var embedder: MediaPipeTextEmbedder? = null
        private var index: VybeIndexFile? = null
        private var library: List<SongText> = emptyList()
        private var started: Job? = null
        private var release: Job? = null

        @Volatile
        private var screenVisible = false

        /** VYBE shown or left: leaving it lets the model go once indexing is done. */
        fun setScreenVisible(visible: Boolean) {
            screenVisible = visible
            release?.cancel()
            if (!visible) {
                release =
                    scope.launch {
                        delay(IDLE_RELEASE_MS)
                        if (_status.value !is VybeStatus.Indexing) releaseModel()
                    }
            }
        }

        /** Starts (once) loading the model and indexing; called when VYBE is opened. */
        fun start() {
            if (started?.isActive == true || !modelFile().isFile) return
            started =
                scope.launch {
                    if (!loadModel()) return@launch
                    libraryRepository
                        .observeSongTags()
                        .distinctUntilChanged()
                        .debounceLibraryChanges()
                        .collect { tags -> reindex(tags.map(VybeText::fromTags)) }
                }
        }

        /** Copies the model the user picked into app storage, then starts. */
        suspend fun importModel(uri: Uri): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val target = modelFile()
                    target.parentFile?.mkdirs()
                    val tmp = File(target.path + ".part")
                    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("The file couldn't be opened")
                    input.use { stream -> tmp.outputStream().use { stream.copyTo(it) } }
                    if (!tmp.renameTo(target)) throw IOException("Couldn't store the model")
                    _status.value = VybeStatus.LoadingModel
                }.onFailure { logger.w(TAG, "Model import failed: ${it.message}") }
            }.also { if (it.isSuccess) start() }

        /** Answers [query] from the indexed songs. */
        suspend fun search(
            query: String,
            limit: Int,
        ): VybeAnswer? =
            withContext(dispatcher) {
                val start = SystemClock.elapsedRealtime()
                Interactions.put(Interactions.VYBE_SEARCH, "searching")
                try {
                    val discovery = discovery() ?: return@withContext null
                    traced("VYBE search") { discovery.search(query, library, limit) }.also {
                        _timings.value = _timings.value.copy(lastQueryMs = SystemClock.elapsedRealtime() - start)
                    }
                } finally {
                    Interactions.put(Interactions.VYBE_SEARCH, null)
                }
            }

        /** Songs nearest to [songId] by description (e.g. the current track). */
        suspend fun similarTo(
            songId: Long,
            limit: Int,
        ): VybeAnswer.SimilarTo? = withContext(dispatcher) { discovery()?.similarToSong(songId, limit) }

        /** Reloads the model if it was released while VYBE was away. */
        @Suppress("ReturnCount")
        private suspend fun discovery(): VybeDiscovery? {
            if (embedder == null && modelFile().isFile && !loadModel()) return null
            if (started?.isActive != true) start()
            val model = embedder ?: return null
            val file = index ?: return null
            val live = currentEntries(file)
            return VybeDiscovery(model, IndexVectors(file, live))
        }

        /** The index entries whose text is still the song's current description. */
        private fun currentEntries(file: VybeIndexFile): Map<Long, Long> {
            val stored = file.entries()
            return library
                .mapNotNull { song ->
                    val hash = documentHash(song)
                    song.songId.takeIf { stored[it] == hash }?.let { it to hash }
                }.toMap()
        }

        private suspend fun loadModel(): Boolean {
            if (embedder != null) return true
            _status.value = VybeStatus.LoadingModel
            Interactions.put(Interactions.VYBE_MODEL, "loading")
            return try {
                val start = SystemClock.elapsedRealtime()
                val model = traced("VYBE load model") { MediaPipeTextEmbedder.fromFile(context, modelFile()) }
                val dimension = model.dimension
                embedder = model
                index = VybeIndexFile(File(context.filesDir, INDEX_FILE), MODEL_ID, dimension)
                _timings.value = _timings.value.copy(modelLoadMs = SystemClock.elapsedRealtime() - start)
                logger.i(TAG, "Model loaded in ${_timings.value.modelLoadMs} ms, dimension $dimension")
                true
            } catch (e: VybeEmbedderException) {
                logger.w(TAG, "Model load failed: ${e.message}")
                _status.value = VybeStatus.Failed("The model couldn't be loaded: ${e.message}")
                false
            } finally {
                Interactions.put(Interactions.VYBE_MODEL, null)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun reindex(songs: List<SongText>) {
            library = songs
            val model = embedder ?: return
            val file = index ?: return
            val stored = file.entries()
            val wanted = songs.associate { it.songId to documentHash(it) }
            val missing = songs.filter { stored[it.songId] != wanted[it.songId] }
            var done = songs.size - missing.size
            _status.value = if (missing.isEmpty()) VybeStatus.Ready(songs.size) else VybeStatus.Indexing(done, songs.size)
            // Songs with the same description (an album's tracks often are) share one embedding.
            val byText = missing.groupBy { VybeText.describe(it) }
            val batch = mutableListOf<IndexedSong>()
            var embedded = 0
            var embedMs = 0L
            if (missing.isNotEmpty()) Interactions.put(Interactions.VYBE_INDEX, "indexing")
            try {
                for ((text, group) in byText) {
                    coroutineContext.ensureActive()
                    val start = SystemClock.elapsedRealtime()
                    val vector = traced("VYBE embed") { model.embed(text, EmbeddingRole.DOCUMENT) }
                    embedMs += SystemClock.elapsedRealtime() - start
                    embedded++
                    group.forEach { batch += IndexedSong(it.songId, wanted.getValue(it.songId), vector) }
                    done += group.size
                    if (batch.size >= APPEND_BATCH) {
                        file.append(batch.toList())
                        batch.clear()
                    }
                    _status.value = VybeStatus.Indexing(done, songs.size)
                    _timings.value = _timings.value.copy(averageEmbedMs = embedMs / embedded)
                    // Let a waiting search run between embeddings (same thread).
                    yield()
                }
                file.append(batch)
                if (file.recordCount() > wanted.size * COMPACT_RATIO + COMPACT_SLACK) file.compact(wanted)
                _status.value = VybeStatus.Ready(songs.size)
                if (!screenVisible) setScreenVisible(false)
                if (embedded > 0) logger.i(TAG, "Indexed $embedded descriptions for ${missing.size} songs, ${embedMs / embedded} ms each")
            } catch (e: CancellationException) {
                file.append(batch)
                throw e
            } catch (e: Exception) {
                file.append(batch)
                logger.w(TAG, "Indexing failed: ${e.message}")
                _status.value = VybeStatus.Failed("Indexing stopped: ${e.message}")
            } finally {
                Interactions.put(Interactions.VYBE_INDEX, null)
            }
        }

        private fun releaseModel() {
            started?.cancel()
            started = null
            embedder?.close()
            embedder = null
            logger.i(TAG, "Model released")
        }

        private fun modelFile() = File(context.filesDir, MODEL_FILE)

        /** The song vectors of [file] limited to the songs whose description is current. */
        private class IndexVectors(
            private val file: VybeIndexFile,
            private val live: Map<Long, Long>,
        ) : SongVectors {
            override fun rank(
                limit: Int,
                score: (songId: Long, vector: FloatArray) -> Float?,
            ): List<VybeMatch> = file.rank(live, limit, score)

            override fun vectorOf(songId: Long): FloatArray? = if (songId in live) file.vectorOf(songId) else null
        }

        companion object {
            private const val TAG = "VybeEngine"
            const val MODEL_FILE = "vybe/embedding_gemma.task"
            private const val INDEX_FILE = "vybe/index.bin"
            private const val MODEL_ID = "embeddinggemma-300m-int4int8|doc${VybeText.DOCUMENT_VERSION}"
            private const val APPEND_BATCH = 8
            private const val COMPACT_RATIO = 2
            private const val COMPACT_SLACK = 64
            private const val LIBRARY_DEBOUNCE_MS = 2_000L
            private const val IDLE_RELEASE_MS = 60_000L

            fun documentHash(song: SongText): Long = textHash(VybeText.describe(song))

            @OptIn(FlowPreview::class)
            private fun <T> kotlinx.coroutines.flow.Flow<T>.debounceLibraryChanges() = debounce(LIBRARY_DEBOUNCE_MS)
        }
    }
