package com.erhu.erhu.core.network.remote.subsonic

import com.erhu.erhu.core.model.RemoteAccessException
import com.erhu.erhu.core.model.RemoteAccessException.Reason
import com.erhu.erhu.core.model.RemoteTrack
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.resume

/**
 * The Subsonic REST API as OpenSubsonic servers (Navidrome among them) implement it. Only standard
 * endpoints are used, so nothing here depends on one particular server.
 *
 * Authentication is the salted token scheme: each request carries a fresh random salt and
 * md5(password + salt), never the password. MD5 is all the protocol offers, which is why plain
 * HTTP is refused unless the user allowed it for that server. Request URLs carry the user name and
 * token, so they must never be logged: [calls] is a client without a logging interceptor.
 */
class SubsonicClient(
    private val calls: Call.Factory,
    private val random: SecureRandom = SecureRandom(),
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            // Some servers send ids and numbers unquoted or quoted interchangeably.
            isLenient = true
        }

    /** Succeeds only when the server answers and accepts the credentials. */
    suspend fun ping(connection: SubsonicConnection) {
        call(connection, "ping")
    }

    suspend fun artists(connection: SubsonicConnection): List<SubsonicArtist> =
        call(connection, "getArtists")
            .artists
            ?.index
            .orEmpty()
            .flatMap { it.artist }
            .filter { it.id.isNotEmpty() }
            .map { it.toModel() }

    suspend fun albumsOfArtist(
        connection: SubsonicConnection,
        artistId: String,
    ): List<SubsonicAlbum> =
        call(connection, "getArtist", "id" to artistId)
            .artist
            ?.album
            .orEmpty()
            .filter { it.id.isNotEmpty() }
            .map { it.toModel() }

    /** One page of all albums, by name. An empty page means the end. */
    suspend fun albums(
        connection: SubsonicConnection,
        offset: Int,
        size: Int,
    ): List<SubsonicAlbum> =
        call(connection, "getAlbumList2", "type" to "alphabeticalByName", "size" to size.toString(), "offset" to offset.toString())
            .albumList2
            ?.album
            .orEmpty()
            .filter { it.id.isNotEmpty() }
            .map { it.toModel() }

    suspend fun albumTracks(
        connection: SubsonicConnection,
        albumId: String,
    ): List<RemoteTrack> =
        call(connection, "getAlbum", "id" to albumId)
            .album
            ?.song
            .orEmpty()
            .toTracks()

    suspend fun search(
        connection: SubsonicConnection,
        query: String,
    ): SubsonicResults =
        call(
            connection,
            "search3",
            "query" to query,
            "artistCount" to SEARCH_ARTISTS.toString(),
            "albumCount" to SEARCH_ALBUMS.toString(),
            "songCount" to SEARCH_SONGS.toString(),
        ).searchResult3?.toModel() ?: SubsonicResults()

    suspend fun playlists(connection: SubsonicConnection): List<SubsonicPlaylist> =
        call(connection, "getPlaylists")
            .playlists
            ?.playlist
            .orEmpty()
            .filter { it.id.isNotEmpty() }
            .map { it.toModel() }

    suspend fun playlistTracks(
        connection: SubsonicConnection,
        playlistId: String,
    ): List<RemoteTrack> =
        call(connection, "getPlaylist", "id" to playlistId)
            .playlist
            ?.entry
            .orEmpty()
            .toTracks()

    /** Everything the user starred on the server. */
    suspend fun starred(connection: SubsonicConnection): SubsonicResults =
        call(connection, "getStarred2").starred2?.toModel() ?: SubsonicResults()

    suspend fun setStarred(
        connection: SubsonicConnection,
        songId: String,
        starred: Boolean,
    ) {
        call(connection, if (starred) "star" else "unstar", "id" to songId)
    }

    /**
     * The original file, not a transcode: servers do not support byte ranges on transcoded
     * streams, and without ranges seeking fails.
     */
    fun streamUrl(
        connection: SubsonicConnection,
        songId: String,
    ): String = url(connection, "stream", listOf("id" to songId, "format" to "raw")).toString()

    /** The cover's bytes, or null when the server has none for [artworkKey]. Blocking. */
    @Throws(IOException::class)
    fun coverArt(
        connection: SubsonicConnection,
        artworkKey: String,
        sizePx: Int,
    ): ByteArray? {
        val request = Request.Builder().url(url(connection, "getCoverArt", listOf("id" to artworkKey, "size" to sizePx.toString()))).build()
        return calls.newCall(request).execute().use { response ->
            // A missing cover comes back as an error document, not as an image.
            val isImage = response.header("Content-Type").orEmpty().startsWith("image/", ignoreCase = true)
            if (!response.isSuccessful || !isImage) null else response.body?.bytes()
        }
    }

    private suspend fun call(
        connection: SubsonicConnection,
        endpoint: String,
        vararg params: Pair<String, String>,
    ): ResponseDto {
        val request = Request.Builder().url(url(connection, endpoint, params.toList())).build()
        val text =
            calls.newCall(request).await { response ->
                if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) {
                    throw RemoteAccessException(Reason.AUTH_REQUIRED, "The server refused the sign-in")
                }
                if (!response.isSuccessful) throw IOException("The server answered with an error (${response.code})")
                response.body?.string().orEmpty()
            }
        val body =
            try {
                json.decodeFromString(EnvelopeDto.serializer(), text).response
            } catch (e: SerializationException) {
                throw IOException("This does not look like a Subsonic-compatible server", e)
            } ?: throw IOException("This does not look like a Subsonic-compatible server")
        if (body.status != "ok") throw body.error.toException()
        return body
    }

    private fun url(
        connection: SubsonicConnection,
        endpoint: String,
        params: List<Pair<String, String>>,
    ): HttpUrl {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes).toHex()
        return connection.baseUrl
            .newBuilder()
            .addPathSegment("rest")
            .addPathSegment("$endpoint.view")
            .apply {
                if (connection.usesApiKey) {
                    // OpenSubsonic API-key sign-in: the key replaces user name, token and salt.
                    addQueryParameter("apiKey", connection.password)
                } else {
                    addQueryParameter("u", connection.username)
                    addQueryParameter("t", md5Hex(connection.password + salt))
                    addQueryParameter("s", salt)
                }
            }.addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
            .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
    }

    private companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "erhu"
        const val SALT_BYTES = 8
        const val SEARCH_ARTISTS = 10
        const val SEARCH_ALBUMS = 20
        const val SEARCH_SONGS = 50
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}

// Subsonic error codes: 40/41/42/43/44 are credential problems, 50 a permission problem, 70 "not found".
private const val ERROR_AUTH_FIRST = 40
private const val ERROR_AUTH_LAST = 44
private const val ERROR_NOT_AUTHORIZED = 50
private const val ERROR_NOT_FOUND = 70

private fun ErrorDto?.toException(): IOException =
    when (this?.code) {
        in ERROR_AUTH_FIRST..ERROR_AUTH_LAST -> RemoteAccessException(Reason.AUTH_REQUIRED, "Wrong user name or password")
        ERROR_NOT_AUTHORIZED -> RemoteAccessException(Reason.AUTH_REQUIRED, "This account is not allowed to do that")
        ERROR_NOT_FOUND -> RemoteAccessException(Reason.NOT_FOUND, "The server no longer has this item")
        else -> IOException(this?.message?.takeIf { it.isNotBlank() } ?: "The server reported an error")
    }

internal fun md5Hex(text: String): String = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** Runs the call off the caller's thread, reads the body with [read], and cancels with the coroutine. */
internal suspend fun <T> Call.await(read: (Response) -> T): T =
    suspendCancellableCoroutine { continuation: CancellableContinuation<Result<T>> ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    continuation.resume(Result.failure(e))
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    continuation.resume(runCatching { response.use(read) })
                }
            },
        )
    }.getOrThrow()
