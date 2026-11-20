package com.erhu.erhu.core.network.remote.drive

import com.erhu.erhu.core.model.RemoteAccessException
import com.erhu.erhu.core.model.RemoteAccessException.Reason
import com.erhu.erhu.core.model.RemoteTrack
import com.erhu.erhu.core.network.remote.subsonic.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

/** Supplies a current Google access token for a connected Drive source. Blocking. */
interface DriveTokenProvider {
    /** Throws [RemoteAccessException] (AUTH_REQUIRED) when the user must sign in again. */
    @Throws(IOException::class)
    fun token(sourceId: Long): String

    /** Drops a token Google rejected, so the next [token] call asks for a fresh one. */
    fun invalidate(sourceId: Long)
}

data class DriveAccount(
    val email: String,
    val name: String,
)

/** A folder or a playable audio file. Shortcuts are already resolved to what they point at. */
data class DriveItem(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val mimeType: String,
    val sizeBytes: Long = 0,
    /** Set for a shared drive's root entry: listings inside it must name the drive. */
    val driveId: String? = null,
) {
    /** The file name is all Drive knows; real tags are read from the file later. */
    fun toTrack() = RemoteTrack(remoteId = id, title = name.substringBeforeLast('.', name), mimeType = mimeType, artworkKey = id)
}

data class DrivePage(
    val items: List<DriveItem>,
    val nextPageToken: String?,
)

enum class DriveOrder(
    val label: String,
    internal val orderBy: String,
) {
    NAME("Name", "folder,name_natural"),
    NEWEST("Newest first", "folder,modifiedTime desc"),
    OLDEST("Oldest first", "folder,modifiedTime"),
}

private val PLAYABLE_EXTENSIONS =
    setOf("mp3", "flac", "m4a", "m4b", "aac", "wav", "ogg", "oga", "opus", "mka", "weba", "amr", "ac3", "eac3")

/** Audio types Drive reports as audio but the player has no extractor for. */
private val UNSUPPORTED_EXTENSIONS = setOf("wma", "ape", "aiff", "aif", "dsf", "dff", "mid", "midi", "ra", "wv", "mpc")

/**
 * Whether the player can be expected to open this file. The extension decides when it is known
 * either way (Drive often labels FLAC or Opus uploads as generic binary); otherwise the media type.
 */
fun isPlayableAudio(
    name: String,
    mimeType: String,
): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase()
    return when {
        extension in PLAYABLE_EXTENSIONS -> !mimeType.startsWith("video/") || extension == "m4a" || extension == "mka"
        extension in UNSUPPORTED_EXTENSIONS -> false
        else -> mimeType.startsWith("audio/") || mimeType == "application/ogg"
    }
}

/**
 * Read-only Google Drive v3 client: only GET requests on `about`, `drives` and `files`. erhu never
 * creates, changes, moves or deletes anything in Drive. The access token travels in the
 * Authorization header, never in a URL, and [calls] is a client without request logging.
 */
class DriveClient(
    private val calls: Call.Factory,
    private val baseUrl: HttpUrl = "https://www.googleapis.com/".toHttpUrl(),
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

    suspend fun about(token: String): DriveAccount {
        val user =
            get<AboutDto>(token, "drive/v3/about") { addQueryParameter("fields", "user(displayName,emailAddress)") }.user
                ?: throw IOException("Google Drive did not say whose account this is")
        return DriveAccount(user.emailAddress, user.displayName)
    }

    /** Folders and playable audio directly inside [folderId] (`root` is My Drive). */
    suspend fun children(
        token: String,
        folderId: String,
        driveId: String?,
        order: DriveOrder,
        pageToken: String?,
    ): DrivePage = files(token, "'${escape(folderId)}' in parents and trashed = false", driveId, order, pageToken)

    suspend fun sharedWithMe(
        token: String,
        order: DriveOrder,
        pageToken: String?,
    ): DrivePage = files(token, "sharedWithMe = true and trashed = false", null, order, pageToken)

    /** File-name search across everything the account can see, shared drives included. */
    suspend fun search(
        token: String,
        text: String,
        pageToken: String?,
    ): DrivePage = files(token, "name contains '${escape(text)}' and trashed = false", null, null, pageToken, allDrives = true)

    suspend fun sharedDrives(
        token: String,
        pageToken: String?,
    ): DrivePage {
        val page =
            get<DrivesDto>(token, "drive/v3/drives") {
                addQueryParameter("pageSize", PAGE_SIZE.toString())
                addQueryParameter("fields", "nextPageToken,drives(id,name)")
                pageToken?.let { addQueryParameter("pageToken", it) }
            }
        return DrivePage(
            page.drives.filter { it.id.isNotEmpty() }.map {
                DriveItem(
                    it.id,
                    it.name,
                    isFolder = true,
                    mimeType = FOLDER,
                    driveId = it.id,
                )
            },
            page.nextPageToken?.takeIf { it.isNotEmpty() },
        )
    }

    /** The file's bytes; needs the Authorization header. Byte ranges are honoured, which is what makes seeking work. */
    fun mediaUrl(fileId: String): String =
        baseUrl
            .newBuilder()
            .addPathSegments("drive/v3/files")
            .addPathSegment(fileId)
            .addQueryParameter("alt", "media")
            .addQueryParameter("supportsAllDrives", "true")
            .build()
            .toString()

    @Suppress("LongParameterList")
    private suspend fun files(
        token: String,
        query: String,
        driveId: String?,
        order: DriveOrder?,
        pageToken: String?,
        allDrives: Boolean = false,
    ): DrivePage {
        val page =
            get<FilesDto>(token, "drive/v3/files") {
                addQueryParameter("q", query)
                addQueryParameter("pageSize", PAGE_SIZE.toString())
                addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,shortcutDetails(targetId,targetMimeType))")
                addQueryParameter("supportsAllDrives", "true")
                addQueryParameter("includeItemsFromAllDrives", "true")
                when {
                    driveId != null -> {
                        addQueryParameter("corpora", "drive")
                        addQueryParameter("driveId", driveId)
                    }
                    allDrives -> addQueryParameter("corpora", "allDrives")
                }
                order?.let { addQueryParameter("orderBy", it.orderBy) }
                pageToken?.let { addQueryParameter("pageToken", it) }
            }
        return DrivePage(page.files.mapNotNull { it.toItem(driveId) }, page.nextPageToken?.takeIf { it.isNotEmpty() })
    }

    private suspend inline fun <reified T> get(
        token: String,
        path: String,
        build: HttpUrl.Builder.() -> Unit,
    ): T {
        val url =
            baseUrl
                .newBuilder()
                .addPathSegments(path)
                .apply(build)
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
        val text =
            calls.newCall(request).await { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> body
                    response.code == HTTP_UNAUTHORIZED -> throw RemoteAccessException(Reason.AUTH_REQUIRED, "Sign in to Google Drive again")
                    // 403 is also Google's answer for rate limits; only a permission problem needs a new sign-in.
                    response.code == HTTP_FORBIDDEN && PERMISSION_MARKERS.any { body.contains(it, ignoreCase = true) } ->
                        throw RemoteAccessException(Reason.AUTH_REQUIRED, "erhu no longer has permission to read this Google Drive")
                    response.code == HTTP_NOT_FOUND -> throw RemoteAccessException(
                        Reason.NOT_FOUND,
                        "This folder or file is no longer in Google Drive",
                    )
                    else -> throw IOException("Google Drive answered with an error (${response.code})")
                }
            }
        return try {
            json.decodeFromString<T>(text)
        } catch (e: SerializationException) {
            throw IOException("Google Drive sent an answer erhu could not read", e)
        }
    }

    /** Drive query strings quote with ' and escape with \. */
    private fun escape(text: String): String = text.replace("\\", "\\\\").replace("'", "\\'")

    private companion object {
        const val PAGE_SIZE = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        val PERMISSION_MARKERS = listOf("insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT", "insufficientFilePermissions")
    }
}

private const val FOLDER = "application/vnd.google-apps.folder"
private const val SHORTCUT = "application/vnd.google-apps.shortcut"

@Serializable
private class AboutDto(
    val user: UserDto? = null,
)

@Serializable
private class UserDto(
    val displayName: String = "",
    val emailAddress: String = "",
)

@Serializable
private class DrivesDto(
    val nextPageToken: String? = null,
    val drives: List<DriveDto> = emptyList(),
)

@Serializable
private class DriveDto(
    val id: String = "",
    val name: String = "",
)

@Serializable
private class FilesDto(
    val nextPageToken: String? = null,
    val files: List<FileDto> = emptyList(),
)

@Serializable
private class ShortcutDto(
    val targetId: String = "",
    val targetMimeType: String = "",
)

@Serializable
private class FileDto(
    val id: String = "",
    val name: String = "",
    val mimeType: String = "",
    val size: Long = 0,
    val shortcutDetails: ShortcutDto? = null,
) {
    /** Null for anything that is neither a folder nor audio the player can open. */
    @Suppress("ReturnCount")
    fun toItem(driveId: String?): DriveItem? {
        val target = shortcutDetails?.takeIf { mimeType == SHORTCUT && it.targetId.isNotEmpty() }
        val realId = target?.targetId ?: id
        val realType = target?.targetMimeType ?: mimeType
        if (realId.isEmpty()) return null
        if (realType == FOLDER) return DriveItem(realId, name, isFolder = true, mimeType = realType, driveId = driveId)
        if (!isPlayableAudio(name, realType)) return null
        return DriveItem(realId, name, isFolder = false, mimeType = realType, sizeBytes = size, driveId = driveId)
    }
}
