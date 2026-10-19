package com.lochanpthakke.empee3.feature.library.builder

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.database.scanner.MediaStoreScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryBuilderIntegrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreScanner: MediaStoreScanner,
    private val logger: Logger
) {
    suspend fun integrate(rootUriString: String) = withContext(Dispatchers.IO) {
        try {
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
                } ?: return@withContext

            logger.d(TAG, "Starting empty folder cleanup")
            cleanupEmptyFolders(rootDoc, isRoot = true)

            logger.d(TAG, "Triggering MediaStore rescan")
            mediaStoreScanner.triggerMediaScannerForStorage()
            
            logger.d(TAG, "Integration complete")
        } catch (e: Exception) {
            logger.e(TAG, "Integration failed: ${e.message}", e)
        }
    }

    private fun cleanupEmptyFolders(dir: DocumentFile, isRoot: Boolean) {
        if (!dir.isDirectory) return
        
        var hasContent = false
        val files = dir.listFiles()
        
        for (file in files) {
            if (file.isDirectory) {
                cleanupEmptyFolders(file, isRoot = false)
                // Re-check if the folder was deleted
                if (file.exists()) {
                    hasContent = true
                }
            } else {
                hasContent = true
            }
        }
        
        if (!hasContent && !isRoot) {
            try {
                dir.delete()
            } catch (e: Exception) {
                logger.d(TAG, "Failed to delete empty folder: ${dir.name}", e)
            }
        }
    }

    companion object {
        private const val TAG = "LibraryBuilderIntegrator"
    }
}
