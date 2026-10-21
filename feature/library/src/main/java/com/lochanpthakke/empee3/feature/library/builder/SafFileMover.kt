package com.lochanpthakke.empee3.feature.library.builder

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.lochanpthakke.empee3.core.database.builder.ProposedMove
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
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
        val rootDoc =
            try {
                if (rootUriString.isBlank()) {
                    null
                } else if (rootUriString.startsWith("content://")) {
                    DocumentFile.fromTreeUri(context, Uri.parse(rootUriString))
                } else {
                    DocumentFile.fromFile(java.io.File(rootUriString))
                }
            } catch (_: Exception) {
                try {
                    DocumentFile.fromFile(java.io.File(rootUriString))
                } catch (_: Exception) {
                    null
                }
            } ?: return@withContext ExecutionResult(0, moves.size, 0, 0)
            
        var successCount = 0
        var failedCount = 0
        var skippedCount = 0
        var alreadyCount = 0
        
        val resolver = context.contentResolver

        moves.forEachIndexed { index, move ->
            if (move.state == ProposedMove.State.ALREADY_ORGANIZED) {
                alreadyCount++
                onProgress(index + 1)
                return@forEachIndexed
            }
            
            if (move.state != ProposedMove.State.VALID) {
                skippedCount++
                onProgress(index + 1)
                return@forEachIndexed
            }
            
            try {
                // Determine destination directory and filename
                val destSegments = move.destinationPath.split("/")
                val fileName = destSegments.last()
                val dirSegments = destSegments.dropLast(1)
                
                var currentDir = rootDoc
                var targetParentUri: Uri? = rootDoc.uri
                
                // Traverse / create directories
                for (segment in dirSegments) {
                    val nextDir = currentDir.findFile(segment) ?: currentDir.createDirectory(segment)
                    if (nextDir == null) {
                        failedCount++
                        onProgress(index + 1)
                        return@forEachIndexed
                    }
                    currentDir = nextDir
                    targetParentUri = nextDir.uri
                }
                
                // Check if destination file already exists
                if (currentDir.findFile(fileName) != null) {
                    // Collision at destination, skip to avoid overwrite
                    failedCount++
                    onProgress(index + 1)
                    return@forEachIndexed
                }
                
                var moved = false
                
                // Attempt moveDocument if API >= 24 and provider is same and source parent is known
                val sourceParentUri = move.sourceParentUri
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && sourceParentUri != null) {
                    try {
                        val movedUri = DocumentsContract.moveDocument(
                            resolver,
                            move.sourceUri,
                            sourceParentUri,
                            targetParentUri!!
                        )
                        if (movedUri != null) {
                            moved = true
                        }
                    } catch (e: Exception) {
                        // moveDocument failed, fallback to copy-delete
                    }
                }
                
                if (!moved) {
                    // Fallback to copy and delete using streams
                    val destFile = currentDir.createFile("application/octet-stream", fileName)
                    if (destFile == null) {
                        failedCount++
                        onProgress(index + 1)
                        return@forEachIndexed
                    }
                    
                    val destFileUri = destFile.uri
                    val copySuccess = try {
                        resolver.openInputStream(move.sourceUri)?.use { input ->
                            resolver.openOutputStream(destFileUri)?.use { output ->
                                input.copyTo(output)
                            }
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                    
                    if (copySuccess) {
                        // Verify destination exists and has data before deleting source
                        val destDoc = DocumentFile.fromSingleUri(context, destFileUri)
                        val destExists = destDoc?.exists() == true
                        val destLength = destDoc?.length() ?: 0L
                        
                        if (destExists && destLength > 0) {
                            val deleted = DocumentFile.fromSingleUri(context, move.sourceUri)?.delete() == true
                            if (deleted) {
                                moved = true
                            } else {
                                // Deletion failed. The requirement says:
                                // "If copying succeeds but deletion fails, the original is preserved and the operation is flagged as a duplicate/failure."
                                failedCount++
                                onProgress(index + 1)
                                return@forEachIndexed
                            }
                        } else {
                            // Copy failed partially, delete destination if possible
                            destFile.delete()
                            failedCount++
                            onProgress(index + 1)
                            return@forEachIndexed
                        }
                    } else {
                        // Copy failed, delete the partially created file
                        destFile.delete()
                        failedCount++
                        onProgress(index + 1)
                        return@forEachIndexed
                    }
                }
                
                if (moved) {
                    successCount++
                } else {
                    failedCount++
                }
                
            } catch (e: Exception) {
                failedCount++
            }
            
            onProgress(index + 1)
        }
        
        ExecutionResult(successCount, failedCount, skippedCount, alreadyCount)
    }
}
