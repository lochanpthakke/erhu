package com.erhu.erhu.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.localizedMessage
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.designsystem.artwork.ArtworkLoader
import com.erhu.erhu.core.model.Genres
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.network.repository.MetadataRepository
import com.erhu.erhu.core.network.tagger.EmbeddedTagData
import com.erhu.erhu.core.network.tagger.EmbeddedTagReader
import com.erhu.erhu.core.network.tagger.FileRenamer
import com.erhu.erhu.core.network.tagger.TagWriteRequest
import com.erhu.erhu.core.network.tagger.writeGenres
import com.erhu.erhu.core.playback.diagnostics.SourceDiagnostics
import com.erhu.erhu.core.playback.diagnostics.SourceInspector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import javax.inject.Inject
import com.erhu.erhu.core.common.R as CommonR

data class EditableInspectorFields(
    val title: String = "",
    val artist: String = "",
    val albumArtist: String = "",
    val album: String = "",
    val year: String = "",
    val genre: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val composer: String = "",
    val comment: String = "",
    val lyrics: String = "",
    val bpm: String = "",
)

data class InspectorUiState(
    val song: Song? = null,
    val filePath: String? = null,
    val tagData: EmbeddedTagData? = null,
    val sourceDiagnostics: SourceDiagnostics? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val userMessage: String? = null,
    val showForceRepairConfirm: Boolean = false,
    val enlargedArtworkBytes: ByteArray? = null,
    val selectedArtworkBytes: ByteArray? = null,
    val isArtworkRemoved: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InspectorUiState) return false

        return song == other.song &&
            filePath == other.filePath &&
            tagData == other.tagData &&
            sourceDiagnostics == other.sourceDiagnostics &&
            isLoading == other.isLoading &&
            isSaving == other.isSaving &&
            userMessage == other.userMessage &&
            showForceRepairConfirm == other.showForceRepairConfirm &&
            isArtworkRemoved == other.isArtworkRemoved &&
            (enlargedArtworkBytes?.contentEquals(other.enlargedArtworkBytes ?: byteArrayOf()) ?: (other.enlargedArtworkBytes == null)) &&
            (selectedArtworkBytes?.contentEquals(other.selectedArtworkBytes ?: byteArrayOf()) ?: (other.selectedArtworkBytes == null))
    }

    override fun hashCode(): Int {
        var result = song?.hashCode() ?: 0
        result = 31 * result + (filePath?.hashCode() ?: 0)
        result = 31 * result + (tagData?.hashCode() ?: 0)
        result = 31 * result + (sourceDiagnostics?.hashCode() ?: 0)
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + isSaving.hashCode()
        result = 31 * result + (userMessage?.hashCode() ?: 0)
        result = 31 * result + showForceRepairConfirm.hashCode()
        result = 31 * result + (enlargedArtworkBytes?.contentHashCode() ?: 0)
        result = 31 * result + (selectedArtworkBytes?.contentHashCode() ?: 0)
        result = 31 * result + isArtworkRemoved.hashCode()
        return result
    }
}

@HiltViewModel
@Suppress("TooManyFunctions", "TooGenericExceptionCaught", "LongMethod")
class ViewMetadataViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
        private val libraryRepository: LibraryRepository,
        private val metadataRepository: MetadataRepository,
        private val embeddedTagReader: EmbeddedTagReader,
        private val sourceInspector: SourceInspector,
        private val fileRenamer: FileRenamer,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(InspectorUiState())
        val uiState: StateFlow<InspectorUiState> = _uiState.asStateFlow()

        private fun localized(): Context = AppLocaleManager.localizedContext(appContext)

        fun inspect(song: Song) {
            viewModelScope.launch {
                _uiState.value = InspectorUiState(song = song, isLoading = true)
                val filePath = libraryRepository.getSongFilePath(song.id)
                if (filePath == null) {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            userMessage = localized().getString(CommonR.string.inspector_msg_no_file),
                        )
                    return@launch
                }

                val tags = embeddedTagReader.readEmbeddedTags(filePath)
                val diag = sourceInspector.inspect(song)

                _uiState.value =
                    InspectorUiState(
                        song = song,
                        filePath = filePath,
                        tagData = tags,
                        sourceDiagnostics = diag,
                        isLoading = false,
                        selectedArtworkBytes = tags.artworkBytes,
                    )
            }
        }

        fun setSelectedArtwork(bytes: ByteArray?) {
            _uiState.value =
                _uiState.value.copy(
                    selectedArtworkBytes = bytes,
                    isArtworkRemoved = bytes == null,
                )
        }

        fun setEnlargedArtwork(bytes: ByteArray?) {
            _uiState.value = _uiState.value.copy(enlargedArtworkBytes = bytes)
        }

        fun setShowForceRepairConfirm(show: Boolean) {
            _uiState.value = _uiState.value.copy(showForceRepairConfirm = show)
        }

        fun consumeMessage() {
            _uiState.value = _uiState.value.copy(userMessage = null)
        }

        fun saveTags(
            fields: EditableInspectorFields,
            renameFile: Boolean = true,
            onSuccess: () -> Unit = {},
        ) {
            val currentState = _uiState.value
            val song = currentState.song ?: return
            val filePath = currentState.filePath ?: return

            viewModelScope.launch {
                _uiState.value = currentState.copy(isSaving = true)

                val artworkBytes = if (currentState.isArtworkRemoved) byteArrayOf() else currentState.selectedArtworkBytes

                val req =
                    TagWriteRequest(
                        contentUri = song.uri,
                        filePath = filePath,
                        title = fields.title.ifBlank { song.title },
                        artist = fields.artist.ifBlank { song.artist },
                        album = fields.album.ifBlank { song.album },
                        year = fields.year.toIntOrNull(),
                        genre = fields.genre.takeIf { it.isNotBlank() },
                        trackNumber = fields.trackNumber.toIntOrNull(),
                        bpm = fields.bpm.toIntOrNull(),
                        lyrics = fields.lyrics.takeIf { it.isNotBlank() },
                        artworkBytes = artworkBytes,
                    )

                when (val result = metadataRepository.writeTagsToFile(req)) {
                    is AppResult.Success -> {
                        ArtworkLoader.invalidate(song.uri)
                        var finalPath = filePath

                        if (renameFile) {
                            val ext = File(filePath).extension.ifBlank { "mp3" }
                            val targetName = fileRenamer.buildTargetFileName(fields.title.ifBlank { song.title }, ext)
                            val renameRes = fileRenamer.renameAudioFile(song.uri, filePath, targetName)
                            if (renameRes is AppResult.Success) {
                                finalPath = renameRes.data.newFilePath
                                libraryRepository.updateFilePath(song.id, finalPath)
                            }
                        }

                        libraryRepository.requestRescanAfterChange()
                        _uiState.value =
                            _uiState.value.copy(
                                isSaving = false,
                                userMessage = localized().getString(CommonR.string.inspector_msg_saved),
                            )
                        onSuccess()
                    }
                    is AppResult.Error -> {
                        val localizedContext = localized()
                        _uiState.value =
                            _uiState.value.copy(
                                isSaving = false,
                                userMessage =
                                    localizedContext.getString(
                                        CommonR.string.inspector_msg_write_failed,
                                        result.error.localizedMessage(localizedContext),
                                    ),
                            )
                    }
                }
            }
        }

        fun executeForceRepair(
            fields: EditableInspectorFields,
            onSuccess: () -> Unit = {},
        ) {
            val currentState = _uiState.value
            val song = currentState.song ?: return
            val filePath = currentState.filePath ?: return

            viewModelScope.launch {
                _uiState.value = currentState.copy(isSaving = true, showForceRepairConfirm = false)

                val result =
                    withContext(Dispatchers.IO) {
                        try {
                            val file = File(filePath)
                            if (!file.exists()) return@withContext false
                            val audioFile = AudioFileIO.read(file)
                            audioFile.tag = audioFile.createDefaultTag()
                            val tag = audioFile.tag

                            if (fields.title.isNotBlank()) tag.setField(FieldKey.TITLE, fields.title)
                            if (fields.artist.isNotBlank()) tag.setField(FieldKey.ARTIST, fields.artist)
                            if (fields.album.isNotBlank()) tag.setField(FieldKey.ALBUM, fields.album)
                            if (fields.year.isNotBlank()) tag.setField(FieldKey.YEAR, fields.year)
                            Genres.parse(fields.genre).takeIf { it.isNotEmpty() }?.let { tag.writeGenres(it) }

                            val artworkBytes = if (currentState.isArtworkRemoved) null else currentState.selectedArtworkBytes
                            artworkBytes?.let { bytes ->
                                val artFile = File(file.parentFile, "force-art.jpg")
                                try {
                                    artFile.writeBytes(bytes)
                                    tag.setField(ArtworkFactory.createArtworkFromFile(artFile))
                                } finally {
                                    artFile.delete()
                                }
                            }

                            AudioFileIO.write(audioFile)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }

                if (result) {
                    ArtworkLoader.invalidate(song.uri)
                    libraryRepository.requestRescanAfterChange()
                    _uiState.value =
                        _uiState.value.copy(
                            isSaving = false,
                            userMessage = localized().getString(CommonR.string.inspector_msg_force_complete),
                        )
                    onSuccess()
                } else {
                    _uiState.value =
                        _uiState.value.copy(
                            isSaving = false,
                            userMessage = localized().getString(CommonR.string.inspector_msg_force_failed),
                        )
                }
            }
        }
    }
