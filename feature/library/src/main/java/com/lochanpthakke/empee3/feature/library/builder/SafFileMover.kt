package com.lochanpthakke.empee3.feature.library.builder

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.lochanpthakke.empee3.core.database.builder.ProposedMove
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ExecutionResult(
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val alreadyOrganizedCount: Int
)

@Singleton
class SafFileMover @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun executeMoves(
        moves: List<ProposedMove>,
        rootUriString: String,
        onProgress: (Int) -> Unit
    ): ExecutionResult = withContext(Dispatchers.IO) {
        var successCount = 0
        var failedCount = 0
        var skippedCount = 0
        var alreadyCount = 0

        val physicalRootDir = resolvePhysicalRootDir(rootUriString)

        moves.forEachIndexed { index, move ->
            try {
                if (move.state == ProposedMove.State.ALREADY_ORGANIZED) {
                    alreadyCount++
                    return@forEachIndexed
                }

                if (move.state != ProposedMove.State.VALID) {
                    skippedCount++
                    return@forEachIndexed
                }

                var movedSuccessfully = tryPhysicalMove(move, physicalRootDir)

                // If physical file moving is restricted by Scoped Storage, fallback to SAF Tree
                if (!movedSuccessfully && rootUriString.startsWith("content://")) {
                    movedSuccessfully = trySafMove(move, rootUriString)
                }

                if (movedSuccessfully) {
                    successCount++
                } else {
                    failedCount++
                }
            } catch (_: Exception) {
                failedCount++
            } finally {
                onProgress(index + 1)
            }
        }

        ExecutionResult(successCount, failedCount, skippedCount, alreadyCount)
    }

    private fun tryPhysicalMove(move: ProposedMove, physicalRootDir: File?): Boolean {
        try {
            val sourceFile = File(move.sourcePath)
            if (!sourceFile.exists() || !sourceFile.isFile) return false

            val targetBaseDir = physicalRootDir ?: sourceFile.parentFile ?: File("/storage/emulated/0/Download")
            val destFile = File(targetBaseDir, move.destinationPath)

            if (sourceFile.canonicalPath.equals(destFile.canonicalPath, ignoreCase = true)) return true

            destFile.parentFile?.mkdirs()

            if (destFile.exists()) {
                if (destFile.length() == 0L) {
                    destFile.delete()
                } else {
                    return false
                }
            }

            // Attempt instantaneous atomic rename
            if (sourceFile.renameTo(destFile)) {
                scanFiles(sourceFile.absolutePath, destFile.absolutePath)
                return true
            }

            // Fallback: Copy via IO streams, then delete
            sourceFile.copyTo(destFile, overwrite = true)
            if (destFile.exists() && destFile.length() == sourceFile.length()) {
                if (sourceFile.delete()) {
                    scanFiles(sourceFile.absolutePath, destFile.absolutePath)
                    return true
                } else {
                    // Rollback if we cannot delete the original file
                    destFile.delete()
                    return false
                }
            } else {
                destFile.delete()
                return false
            }
        } catch (_: Exception) {
            return false
        }
    }

    private fun trySafMove(move: ProposedMove, rootUriString: String): Boolean {
        try {
            val treeUri = Uri.parse(rootUriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            val resolver = context.contentResolver

            val destSegments = move.destinationPath.split("/")
            val fileName = destSegments.last()
            val dirSegments = destSegments.dropLast(1)

            // Traverse and create target directories
            var currentDir = rootDoc
            for (segment in dirSegments) {
                val nextDir = currentDir.findFile(segment) ?: currentDir.createDirectory(segment)
                if (nextDir == null) return false
                currentDir = nextDir
            }

            val existingFile = currentDir.findFile(fileName)
            if (existingFile != null) {
                if (existingFile.length() == 0L) {
                    existingFile.delete()
                } else {
                    return false
                }
            }

            // 1. Try native SAF Move (Supported on Android 7+ within the same provider)
            val sourceParent = move.sourceParentUri
            if (sourceParent != null) {
                try {
                    val movedUri = DocumentsContract.moveDocument(resolver, move.sourceUri, sourceParent, currentDir.uri)
                    if (movedUri != null) return true
                } catch (_: Exception) {}
            }

            // 2. Fallback SAF Stream Copy & Delete
            val destFile = currentDir.createFile("audio/*", fileName) ?: return false

            var copySuccess = false
            try {
                resolver.openInputStream(move.sourceUri)?.use { input ->
                    resolver.openOutputStream(destFile.uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                // If it reaches here without IOException, the bytes were successfully written
                copySuccess = true
            } catch (_: Exception) {}

            if (copySuccess) {
                // Delete the original file
                val deleted = try {
                    DocumentsContract.deleteDocument(resolver, move.sourceUri)
                } catch (_: Exception) {
                    try {
                        DocumentFile.fromSingleUri(context, move.sourceUri)?.delete() == true
                    } catch (_: Exception) { false }
                }

                if (deleted) {
                    return true
                } else {
                    // Rollback copy if we failed to delete the original
                    destFile.delete()
                    return false
                }
            } else {
                destFile.delete()
                return false
            }
        } catch (_: Exception) {
            return false
        }
    }

    private fun resolvePhysicalRootDir(rootUriString: String): File? {
        if (rootUriString.isBlank()) return null
        if (!rootUriString.startsWith("content://")) {
            val f = File(rootUriString)
            if (f.exists() || f.mkdirs()) return f
        }

        try {
            val uri = Uri.parse(rootUriString)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            if (docId.startsWith("primary:")) {
                val relPath = docId.substringAfter(":")
                val f = File("/storage/emulated/0", relPath)
                if (f.exists() || f.mkdirs()) return f
            }
        } catch (_: Exception) {
        }

        return null
    }

    private fun scanFiles(vararg paths: String) {
        try {
            MediaScannerConnection.scanFile(context, paths, null, null)
        } catch (_: Exception) {}
    }
}
