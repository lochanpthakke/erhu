package com.lochanpthakke.empee3.core.database.builder

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.documentfile.provider.DocumentFile
import com.lochanpthakke.empee3.core.database.library.PrimaryArtistExtractor
import com.lochanpthakke.empee3.core.database.scanner.JAudioTaggerEnricher
import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.jaudiotagger.tag.FieldKey
import javax.inject.Inject
import javax.inject.Singleton

data class AnalysisResult(
    val rootUriString: String,
    val moves: List<ProposedMove>,
)

@Singleton
class LibraryBuilderAnalyzer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val jaudioTaggerEnricher: JAudioTaggerEnricher,
        private val dataStore: DataStore<PlaybackStateProto>,
    ) {
        private val supportedExtensions = setOf("mp3", "flac", "m4a", "ogg", "wav", "aac")

        suspend fun analyze(
            customUriString: String = "",
            forceReorganize: Boolean = false,
        ): AnalysisResult {
            val targetUri =
                if (customUriString.isNotBlank()) {
                    customUriString
                } else {
                    val proto = dataStore.data.first()
                    proto.customScanLocationUri.ifBlank { proto.customScanLocationPath }
                }

            if (targetUri.isBlank()) return AnalysisResult("", emptyList())
            val rootDocument =
                try {
                    if (targetUri.startsWith("content://")) {
                        DocumentFile.fromTreeUri(context, Uri.parse(targetUri))
                    } else {
                        DocumentFile.fromFile(java.io.File(targetUri))
                    }
                } catch (_: Exception) {
                    try {
                        DocumentFile.fromFile(java.io.File(targetUri))
                    } catch (_: Exception) {
                        null
                    }
                } ?: return AnalysisResult(targetUri, emptyList())

            val results = mutableListOf<ProposedMove>()
            scanDirectory(rootDocument, results)

            val destCount = results.groupingBy { it.destinationPath.lowercase() }.eachCount()
            val finalResults =
                results.mapNotNull { move ->
                    if (move.state == ProposedMove.State.ALREADY_ORGANIZED) {
                        if (forceReorganize) {
                            val nextState =
                                if (destCount.getOrDefault(move.destinationPath.lowercase(), 0) > 1) {
                                    ProposedMove.State.COLLISION
                                } else {
                                    ProposedMove.State.VALID
                                }
                            Log.i(TAG, "force re-organize: ALREADY_ORGANIZED -> $nextState '${move.sourcePath}'")
                            move.copy(state = nextState)
                        } else {
                            null
                        }
                    } else if (destCount.getOrDefault(move.destinationPath.lowercase(), 0) > 1 &&
                        move.state != ProposedMove.State.MISSING_METADATA
                    ) {
                        move.copy(state = ProposedMove.State.COLLISION)
                    } else {
                        move
                    }
                }

            Log.i(
                TAG,
                "analyze root='$targetUri' force=$forceReorganize scanned=${results.size} proposed=${finalResults.groupingBy {
                    it.state
                }.eachCount()}",
            )
            return AnalysisResult(targetUri, finalResults)
        }

        private fun scanDirectory(
            dir: DocumentFile,
            results: MutableList<ProposedMove>,
        ) {
            dir.listFiles().forEach { file ->
                if (file.isDirectory) {
                    scanDirectory(file, results)
                } else {
                    val name = file.name ?: ""
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in supportedExtensions) {
                        processFile(file, dir.uri, results)
                    }
                }
            }
        }

        private fun processFile(
            file: DocumentFile,
            parentUri: Uri?,
            results: MutableList<ProposedMove>,
        ) {
            val uri = file.uri
            val sourcePath =
                resolveFilePath(uri) ?: run {
                    Log.w(TAG, "no file path for ${file.name}, not offered")
                    return
                }
            val ext = file.name?.substringAfterLast('.', "")?.lowercase() ?: ""

            val title = jaudioTaggerEnricher.readTextTag(sourcePath, FieldKey.TITLE) ?: "Unknown Title"
            val artist = jaudioTaggerEnricher.readTextTag(sourcePath, FieldKey.ARTIST) ?: "Unknown Artist"
            val album = jaudioTaggerEnricher.readTextTag(sourcePath, FieldKey.ALBUM) ?: "Unknown Album"
            val albumArtist = jaudioTaggerEnricher.readTextTag(sourcePath, FieldKey.ALBUM_ARTIST)

            val primaryArtist =
                PrimaryArtistExtractor.extractPrimaryArtist(albumArtist)
                    ?: PrimaryArtistExtractor.extractPrimaryArtist(artist)
                    ?: "Unknown Artist"

            val cleanArtist = sanitizeFileName(primaryArtist)
            val cleanAlbum = sanitizeFileName(album)
            val cleanTitle = sanitizeFileName(title)

            val relativeDest = "$cleanArtist/$cleanAlbum/$cleanTitle.$ext"

            val hasMissingMetadata = (title == "Unknown Title") || (primaryArtist == "Unknown Artist") || (album == "Unknown Album")
            val isAlreadyOrganized = sourcePath.endsWith("/$relativeDest", ignoreCase = true)

            val state =
                when {
                    hasMissingMetadata -> ProposedMove.State.MISSING_METADATA
                    isAlreadyOrganized -> ProposedMove.State.ALREADY_ORGANIZED
                    else -> ProposedMove.State.VALID
                }

            results.add(
                ProposedMove(
                    sourceUri = uri,
                    sourceParentUri = parentUri,
                    sourcePath = sourcePath,
                    destinationPath = relativeDest,
                    title = title,
                    artist = primaryArtist,
                    album = album,
                    state = state,
                ),
            )
        }

        private fun resolveFilePath(uri: Uri): String? {
            try {
                context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val path = cursor.getString(0)
                        if (!path.isNullOrBlank()) return path
                    }
                }
            } catch (_: Exception) {
            }

            try {
                val docId = DocumentsContract.getDocumentId(uri)
                if (docId.startsWith("primary:")) {
                    return "/storage/emulated/0/" + docId.substringAfter(":")
                }
            } catch (_: Exception) {
            }

            return null
        }

        private companion object {
            const val TAG = "LibraryBuilderMover"
        }

        private fun sanitizeFileName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }
