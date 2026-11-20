package com.erhu.erhu.feature.library.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ShareCardExporter {
    suspend fun saveToCache(
        context: Context,
        bitmap: Bitmap,
        prefix: String,
    ): Uri =
        withContext(Dispatchers.IO) {
            val shareDir = File(context.cacheDir, "share_cards")
            shareDir.mkdirs()

            // Clean up files older than 1 hour to prevent cache bloat
            val oneHourAgo = System.currentTimeMillis() - 3600_000L
            shareDir.listFiles()?.filter { it.lastModified() < oneHourAgo }?.forEach { it.delete() }

            val file = File(shareDir, "${prefix}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        }

    fun shareImage(
        context: Context,
        contentUri: Uri,
        title: String,
    ) {
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val chooser =
            Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(chooser)
    }

    suspend fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        title: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            var uri: Uri? = null
            try {
                val filename = "${title.replace(Regex("[^a-zA-Z0-9_]"), "_")}_${System.currentTimeMillis()}.png"
                val values =
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Erhu")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }

                val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                uri = target
                val written =
                    resolver.openOutputStream(target)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } == true
                if (!written) {
                    resolver.delete(target, null, null)
                    return@withContext false
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(target, values, null, null)
                }
                true
            } catch (e: Exception) {
                // Never leave a pending, half-written entry in the gallery.
                uri?.let { runCatching { resolver.delete(it, null, null) } }
                false
            }
        }
}
