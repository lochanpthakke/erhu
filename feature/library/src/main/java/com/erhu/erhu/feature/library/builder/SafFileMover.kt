package com.erhu.erhu.feature.library.builder

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.database.builder.ProposedMove
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.erhu.erhu.core.common.R as CommonR

data class ExecutionResult(
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val alreadyOrganizedCount: Int,
    /** Failure stage -> number of files that stopped there. */
    val failureReasons: Map<MoveOutcome, Int> = emptyMap(),
)

/** Final result of one proposed move; the name says the stage where it ended. */
enum class MoveOutcome(
    val isSuccess: Boolean,
    val label: String,
    @param:StringRes val labelRes: Int,
) {
    SUCCESS_PHYSICAL(true, "Moved", CommonR.string.builder_outcome_moved),
    SUCCESS_SAF_RENAME(true, "Renamed", CommonR.string.builder_outcome_renamed),
    SUCCESS_SAF_MOVE(true, "Moved", CommonR.string.builder_outcome_moved),
    SUCCESS_SAF_COPY_DELETE(true, "Copied, then original removed", CommonR.string.builder_outcome_copied_removed),
    ALREADY_AT_DESTINATION(false, "Already in place", CommonR.string.builder_outcome_already_in_place),
    SKIPPED_STATE(false, "Skipped", CommonR.string.builder_outcome_skipped),
    FAILED_SOURCE_NOT_FOUND(false, "Source file not found", CommonR.string.builder_outcome_source_not_found),
    FAILED_DESTINATION_EXISTS(false, "A file with that name is already there", CommonR.string.builder_outcome_dest_exists),
    FAILED_DIRECTORY_CREATION(false, "Could not create the folder", CommonR.string.builder_outcome_dir_create),
    FAILED_NO_WRITE_PERMISSION(
        false,
        "No write access to the folder: choose it again in Settings > Scan location",
        CommonR.string.builder_outcome_no_write,
    ),
    FAILED_RENAME(false, "Rename failed", CommonR.string.builder_outcome_rename_failed),
    FAILED_MOVED_NOT_RENAMED(
        false,
        "Moved, but the file name could not be changed",
        CommonR.string.builder_outcome_moved_not_renamed,
    ),
    FAILED_COPY(false, "Copy failed (original kept)", CommonR.string.builder_outcome_copy_failed),
    FAILED_DELETE_SOURCE(
        false,
        "Original could not be removed (copy undone, original kept)",
        CommonR.string.builder_outcome_delete_source_failed,
    ),
    FAILED_AMBIGUOUS(
        false,
        "Storage gave an unclear answer (nothing else was changed)",
        CommonR.string.builder_outcome_ambiguous,
    ),
    FAILED_UNKNOWN(false, "Unexpected error", CommonR.string.builder_outcome_unknown),
    ;

    fun localizedLabel(context: Context): String = AppLocaleManager.localizedContext(context).getString(labelRes)
}

@Singleton
class SafFileMover
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun executeMoves(
            moves: List<ProposedMove>,
            rootUriString: String,
            onProgress: (Int) -> Unit,
        ): ExecutionResult =
            withContext(Dispatchers.IO) {
                var successCount = 0
                var failedCount = 0
                var skippedCount = 0
                var alreadyCount = 0
                val outcomes = mutableListOf<MoveOutcome>()

                val isContentRoot = rootUriString.startsWith("content://")
                Log.i(
                    TAG,
                    "=== executeMoves root='$rootUriString' type=${if (isContentRoot) "content-uri" else "filesystem-path"} moves=${moves.size}",
                )
                Log.i(TAG, "states: ${moves.groupingBy { it.state }.eachCount()}")
                logStorageAccess()

                val physicalRootDir = resolvePhysicalRootDir(rootUriString)
                Log.i(TAG, "physical root: ${physicalRootDir?.absolutePath ?: "NOT RESOLVED"} canWrite=${physicalRootDir?.canWrite()}")

                val safRoot = if (isContentRoot) resolveSafRoot(rootUriString) else null

                moves.forEachIndexed { index, move ->
                    val tag = "[${index + 1}/${moves.size}]"
                    Log.i(
                        TAG,
                        "$tag state=${move.state} sourcePath='${move.sourcePath}' sourceUri=${move.sourceUri} sourceParentUri=${move.sourceParentUri} dest='${move.destinationPath}'",
                    )
                    val outcome =
                        try {
                            executeOne(tag, move, physicalRootDir, safRoot)
                        } catch (e: Exception) {
                            Log.e(TAG, "$tag unexpected ${e.javaClass.name}: ${e.message}", e)
                            MoveOutcome.FAILED_UNKNOWN
                        }
                    outcomes += outcome
                    when {
                        outcome.isSuccess -> successCount++
                        outcome == MoveOutcome.ALREADY_AT_DESTINATION -> alreadyCount++
                        outcome == MoveOutcome.SKIPPED_STATE -> skippedCount++
                        else -> failedCount++
                    }
                    Log.i(TAG, "$tag OUTCOME=$outcome")
                    onProgress(index + 1)
                }

                val failures =
                    outcomes
                        .filterNot { it.isSuccess || it == MoveOutcome.ALREADY_AT_DESTINATION || it == MoveOutcome.SKIPPED_STATE }
                        .groupingBy { it }
                        .eachCount()
                val outcomeCounts = outcomes.groupingBy { it }.eachCount()
                Log.i(
                    TAG,
                    "=== summary success=$successCount failed=$failedCount skipped=$skippedCount " +
                        "already=$alreadyCount outcomes=$outcomeCounts",
                )
                ExecutionResult(successCount, failedCount, skippedCount, alreadyCount, failures)
            }

        private fun executeOne(
            tag: String,
            move: ProposedMove,
            physicalRootDir: File?,
            safRoot: DocumentFile?,
        ): MoveOutcome {
            if (move.state == ProposedMove.State.ALREADY_ORGANIZED) return MoveOutcome.ALREADY_AT_DESTINATION
            if (move.state != ProposedMove.State.VALID) return MoveOutcome.SKIPPED_STATE

            if (move.sourcePath.endsWith("/" + move.destinationPath, ignoreCase = true)) {
                // Force Re-organize turns ALREADY_ORGANIZED entries into VALID ones; they end up here.
                Log.i(TAG, "$tag source already ends with the destination path (Force Re-organize entry)")
            }

            val physical = tryPhysicalMove(tag, move, physicalRootDir)
            Log.i(TAG, "$tag physical result=$physical")
            if (physical.isSuccess ||
                physical == MoveOutcome.ALREADY_AT_DESTINATION ||
                physical == MoveOutcome.FAILED_DESTINATION_EXISTS ||
                physical == MoveOutcome.FAILED_AMBIGUOUS
            ) {
                return physical
            }
            if (safRoot == null) {
                Log.w(TAG, "$tag no SAF root, nothing else to try")
                return physical
            }
            return trySafMove(tag, move, safRoot)
        }

        private fun tryPhysicalMove(
            tag: String,
            move: ProposedMove,
            physicalRootDir: File?,
        ): MoveOutcome {
            if (physicalRootDir == null) {
                Log.w(TAG, "$tag physical: skipped, root directory not resolved")
                return MoveOutcome.FAILED_UNKNOWN
            }
            try {
                val sourceFile = File(move.sourcePath)
                val exists = sourceFile.exists()
                val isFile = sourceFile.isFile
                Log.i(
                    TAG,
                    "$tag physical: source exists=$exists isFile=$isFile canRead=${sourceFile.canRead()} parentCanWrite=${sourceFile.parentFile?.canWrite()}",
                )
                if (!exists || !isFile) return MoveOutcome.FAILED_SOURCE_NOT_FOUND

                val destFile = File(physicalRootDir, move.destinationPath)
                val destCanonical = destFile.canonicalPath
                val samePath = sourceFile.canonicalPath.equals(destCanonical, ignoreCase = true)
                Log.i(TAG, "$tag physical: dest='$destCanonical' samePath=$samePath")
                if (samePath) return MoveOutcome.ALREADY_AT_DESTINATION

                if (destFile.exists()) {
                    // Never delete or overwrite what is already there, not even an empty file.
                    Log.w(TAG, "$tag physical: destination exists (length=${destFile.length()}), refusing")
                    return MoveOutcome.FAILED_DESTINATION_EXISTS
                }

                val destDir = destFile.parentFile ?: return MoveOutcome.FAILED_DIRECTORY_CREATION
                val dirExisted = destDir.isDirectory
                val mkdirs = !dirExisted && destDir.mkdirs()
                Log.i(
                    TAG,
                    "$tag physical: destDir existed=$dirExisted mkdirs=$mkdirs isDirectoryNow=${destDir.isDirectory} canWrite=${destDir.canWrite()}",
                )
                if (!destDir.isDirectory) return MoveOutcome.FAILED_DIRECTORY_CREATION

                val sourceLength = sourceFile.length()
                val renamed = sourceFile.renameTo(destFile)
                val sourceGone = !sourceFile.exists()
                val destLength = if (destFile.exists()) destFile.length() else -1L
                Log.i(TAG, "$tag physical: renameTo=$renamed sourceGone=$sourceGone destLength=$destLength sourceLength=$sourceLength")
                if (renamed && sourceGone && destLength == sourceLength) {
                    scanFiles(sourceFile.absolutePath, destFile.absolutePath)
                    return MoveOutcome.SUCCESS_PHYSICAL
                }
                if (renamed) {
                    Log.e(TAG, "$tag physical: renameTo returned true but the files do not check out; leaving them as they are")
                    return MoveOutcome.FAILED_AMBIGUOUS
                }
                // No stream-copy fallback here: deleting a file this app does not own fails under scoped
                // storage. The SAF path copies, verifies and deletes with rollback.
                return MoveOutcome.FAILED_RENAME
            } catch (e: Exception) {
                Log.e(TAG, "$tag physical: ${e.javaClass.name}: ${e.message}")
                return if (e is SecurityException) MoveOutcome.FAILED_NO_WRITE_PERMISSION else MoveOutcome.FAILED_UNKNOWN
            }
        }

        private fun trySafMove(
            tag: String,
            move: ProposedMove,
            rootDoc: DocumentFile,
        ): MoveOutcome {
            val resolver = context.contentResolver
            val destSegments = move.destinationPath.split("/").filter { it.isNotBlank() }
            val fileName = destSegments.lastOrNull() ?: return MoveOutcome.FAILED_UNKNOWN
            val dirSegments = destSegments.dropLast(1)

            // 1. Destination folders.
            var currentDir = rootDoc
            for (segment in dirSegments) {
                val found = currentDir.findFile(segment)
                val nextDir =
                    if (found != null) {
                        Log.i(TAG, "$tag saf: dir '$segment' found isDirectory=${found.isDirectory}")
                        found
                    } else {
                        try {
                            currentDir.createDirectory(segment).also {
                                Log.i(TAG, "$tag saf: dir '$segment' created=${it != null} name='${it?.name}'")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "$tag saf: createDirectory('$segment') ${e.javaClass.name}: ${e.message}")
                            return if (e is SecurityException) {
                                MoveOutcome.FAILED_NO_WRITE_PERMISSION
                            } else {
                                MoveOutcome.FAILED_DIRECTORY_CREATION
                            }
                        }
                    }
                if (nextDir == null || !nextDir.isDirectory) {
                    Log.e(TAG, "$tag saf: directory traversal failed at '$segment'")
                    return MoveOutcome.FAILED_DIRECTORY_CREATION
                }
                if (nextDir.name != segment) {
                    Log.w(TAG, "$tag saf: provider named the folder '${nextDir.name}' instead of '$segment'")
                }
                currentDir = nextDir
            }
            Log.i(TAG, "$tag saf: destination dir '${currentDir.name}' uri=${currentDir.uri}")

            // 2. Source.
            val sourceDoc = DocumentFile.fromSingleUri(context, move.sourceUri)
            val sourceExists = sourceDoc?.exists() == true
            val sourceLength = sourceDoc?.length() ?: 0L
            val sourceParent = move.sourceParentUri
            val sameParent = sourceParent != null && sameDocument(sourceParent, currentDir.uri)
            Log.i(
                TAG,
                "$tag saf: source name='${sourceDoc?.name}' exists=$sourceExists length=$sourceLength parent=$sourceParent sameParent=$sameParent",
            )
            if (!sourceExists) return MoveOutcome.FAILED_SOURCE_NOT_FOUND

            // 3. Name already taken?
            val existing = currentDir.findFile(fileName)
            if (existing != null) {
                if (sameDocument(existing.uri, move.sourceUri)) {
                    Log.i(TAG, "$tag saf: destination is the source itself")
                    return MoveOutcome.ALREADY_AT_DESTINATION
                }
                Log.w(TAG, "$tag saf: destination name exists (length=${existing.length()}), refusing")
                return MoveOutcome.FAILED_DESTINATION_EXISTS
            }

            // 4a. Right folder, different name: rename only.
            if (sameParent) {
                return renameDocument(
                    tag,
                    move.sourceUri,
                    fileName,
                    sourceLength,
                    MoveOutcome.SUCCESS_SAF_RENAME,
                    MoveOutcome.FAILED_RENAME,
                )
            }

            // 4b. Native move (same provider).
            if (sourceParent != null) {
                val movedUri =
                    try {
                        DocumentsContract.moveDocument(resolver, move.sourceUri, sourceParent, currentDir.uri)
                    } catch (e: Exception) {
                        Log.w(TAG, "$tag saf: moveDocument threw ${e.javaClass.name}: ${e.message}")
                        if (e is SecurityException) return MoveOutcome.FAILED_NO_WRITE_PERMISSION
                        null
                    }
                Log.i(TAG, "$tag saf: moveDocument returned $movedUri")
                if (movedUri != null) {
                    val movedDoc = DocumentFile.fromSingleUri(context, movedUri)
                    val movedExists = movedDoc?.exists() == true
                    val movedLength = movedDoc?.length() ?: -1L
                    val sourceStillThere = DocumentFile.fromSingleUri(context, move.sourceUri)?.exists() == true
                    Log.i(
                        TAG,
                        "$tag saf: after move exists=$movedExists length=$movedLength sourceStillThere=$sourceStillThere name='${movedDoc?.name}'",
                    )
                    if (!movedExists || sourceStillThere || movedLength != sourceLength) return MoveOutcome.FAILED_AMBIGUOUS
                    if (movedDoc?.name == fileName) return MoveOutcome.SUCCESS_SAF_MOVE
                    // moveDocument keeps the old file name; give it the planned one.
                    return renameDocument(
                        tag,
                        movedUri,
                        fileName,
                        sourceLength,
                        MoveOutcome.SUCCESS_SAF_MOVE,
                        MoveOutcome.FAILED_MOVED_NOT_RENAMED,
                    )
                }
            } else {
                Log.w(TAG, "$tag saf: no source parent URI, moveDocument not possible")
            }

            // 4c. Copy, verify, then delete the original.
            return copyThenDelete(tag, move, currentDir, fileName, sourceLength)
        }

        private fun renameDocument(
            tag: String,
            uri: Uri,
            fileName: String,
            expectedLength: Long,
            success: MoveOutcome,
            failure: MoveOutcome,
        ): MoveOutcome {
            val renamedUri =
                try {
                    DocumentsContract.renameDocument(context.contentResolver, uri, fileName)
                } catch (e: Exception) {
                    Log.e(TAG, "$tag saf: renameDocument threw ${e.javaClass.name}: ${e.message}")
                    return if (e is SecurityException) MoveOutcome.FAILED_NO_WRITE_PERMISSION else failure
                }
            Log.i(TAG, "$tag saf: renameDocument('$fileName') returned $renamedUri")
            if (renamedUri == null) return failure
            val doc = DocumentFile.fromSingleUri(context, renamedUri)
            val exists = doc?.exists() == true
            val length = doc?.length() ?: -1L
            Log.i(TAG, "$tag saf: after rename exists=$exists name='${doc?.name}' length=$length")
            return if (exists && doc?.name == fileName && length == expectedLength) success else MoveOutcome.FAILED_AMBIGUOUS
        }

        private fun copyThenDelete(
            tag: String,
            move: ProposedMove,
            destDir: DocumentFile,
            fileName: String,
            sourceLength: Long,
        ): MoveOutcome {
            val resolver = context.contentResolver
            Log.i(TAG, "$tag saf: copy fallback")
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            val destFile =
                try {
                    destDir.createFile(mime, fileName)
                } catch (e: Exception) {
                    Log.e(TAG, "$tag saf: createFile threw ${e.javaClass.name}: ${e.message}")
                    return if (e is SecurityException) MoveOutcome.FAILED_NO_WRITE_PERMISSION else MoveOutcome.FAILED_COPY
                }
            Log.i(TAG, "$tag saf: createFile($mime) name='${destFile?.name}' uri=${destFile?.uri}")
            if (destFile == null) return MoveOutcome.FAILED_COPY
            if (destFile.name != fileName) {
                // The provider chose another name (e.g. "Title (1).mp3"), so something is in the way.
                Log.w(TAG, "$tag saf: provider renamed the new file; undoing it")
                deleteOwnCopy(tag, destFile)
                return MoveOutcome.FAILED_DESTINATION_EXISTS
            }

            var copied = -1L
            try {
                val input = resolver.openInputStream(move.sourceUri)
                Log.i(TAG, "$tag saf: openInputStream ok=${input != null}")
                input?.use { source ->
                    val output = resolver.openOutputStream(destFile.uri, "w")
                    Log.i(TAG, "$tag saf: openOutputStream ok=${output != null}")
                    output?.use { sink ->
                        copied = source.copyTo(sink)
                        sink.flush()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "$tag saf: copy threw ${e.javaClass.name}: ${e.message}")
            }
            // Streams are closed here; check what actually landed.
            val destLength = DocumentFile.fromSingleUri(context, destFile.uri)?.length() ?: -1L
            Log.i(TAG, "$tag saf: copied=$copied bytes destLength=$destLength sourceLength=$sourceLength")
            if (copied <= 0L || copied != sourceLength || destLength != sourceLength) {
                deleteOwnCopy(tag, destFile)
                return MoveOutcome.FAILED_COPY
            }

            val deleted =
                try {
                    DocumentsContract.deleteDocument(resolver, move.sourceUri)
                } catch (e: Exception) {
                    Log.e(TAG, "$tag saf: deleteDocument(source) threw ${e.javaClass.name}: ${e.message}")
                    false
                }
            val sourceStillThere = DocumentFile.fromSingleUri(context, move.sourceUri)?.exists() == true
            Log.i(TAG, "$tag saf: delete source=$deleted sourceStillThere=$sourceStillThere")
            if (deleted && !sourceStillThere) return MoveOutcome.SUCCESS_SAF_COPY_DELETE
            if (!sourceStillThere) {
                // Source is gone although delete reported failure: keep the verified copy.
                Log.w(TAG, "$tag saf: source vanished despite delete=false; keeping the verified copy")
                return MoveOutcome.FAILED_AMBIGUOUS
            }
            // Original intact: undo our copy so no duplicate is left behind.
            deleteOwnCopy(tag, destFile)
            return MoveOutcome.FAILED_DELETE_SOURCE
        }

        /** Deletes only the file this attempt created. */
        private fun deleteOwnCopy(
            tag: String,
            file: DocumentFile,
        ) {
            val ok =
                try {
                    file.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "$tag saf: cleanup threw ${e.javaClass.name}: ${e.message}")
                    false
                }
            Log.i(TAG, "$tag saf: cleanup of '${file.name}' deleted=$ok")
        }

        private fun sameDocument(
            a: Uri,
            b: Uri,
        ): Boolean =
            try {
                a.authority == b.authority && DocumentsContract.getDocumentId(a) == DocumentsContract.getDocumentId(b)
            } catch (_: Exception) {
                a == b
            }

        private fun resolveSafRoot(rootUriString: String): DocumentFile? =
            try {
                DocumentFile.fromTreeUri(context, Uri.parse(rootUriString)).also {
                    Log.i(
                        TAG,
                        "SAF root: resolved=${it != null} name='${it?.name}' exists=${it?.exists()} canRead=${it?.canRead()} canWrite=${it?.canWrite()}",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "SAF root: ${e.javaClass.name}: ${e.message}")
                null
            }

        private fun logStorageAccess() {
            val allFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
            Log.i(TAG, "all-files access granted=$allFiles sdk=${Build.VERSION.SDK_INT}")
            context.contentResolver.persistedUriPermissions.forEach {
                Log.i(TAG, "persisted grant: ${it.uri} read=${it.isReadPermission} write=${it.isWritePermission}")
            }
        }

        private fun resolvePhysicalRootDir(rootUriString: String): File? {
            if (rootUriString.isBlank()) return null
            if (!rootUriString.startsWith("content://")) {
                return File(rootUriString).takeIf { it.isDirectory }
            }
            try {
                val docId = DocumentsContract.getTreeDocumentId(Uri.parse(rootUriString))
                Log.i(TAG, "tree document id='$docId'")
                if (docId.startsWith("primary:")) {
                    return File("/storage/emulated/0", docId.substringAfter(":")).takeIf { it.isDirectory }
                }
            } catch (e: Exception) {
                Log.w(TAG, "physical root: ${e.javaClass.name}: ${e.message}")
            }
            return null
        }

        private fun scanFiles(vararg paths: String) {
            try {
                MediaScannerConnection.scanFile(context, paths, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "scanFile: ${e.javaClass.name}: ${e.message}")
            }
        }

        private companion object {
            const val TAG = "LibraryBuilderMover"
        }
    }
