package com.erhu.erhu.core.network.repository

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.common.result.AppError
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.network.api.GitHubUpdateApi
import com.erhu.erhu.core.network.model.GitHubAssetDto
import com.erhu.erhu.core.network.update.InstalledAppVersion
import com.erhu.erhu.core.network.update.PublishedAppVersion
import com.erhu.erhu.core.network.update.UpdateCheck
import com.erhu.erhu.core.network.update.UpdateVersion
import com.erhu.erhu.core.network.update.selectReleaseApk
import com.erhu.erhu.core.network.update.trustedReleaseAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateCheckerRepositoryImpl internal constructor(
    private val api: GitHubUpdateApi,
    private val logger: Logger,
    private val manifest: suspend (GitHubAssetDto) -> PublishedAppVersion?,
    private val now: () -> Long = System::currentTimeMillis,
) : UpdateCheckerRepository {
    @Inject
    constructor(api: GitHubUpdateApi, logger: Logger, client: OkHttpClient, json: Json) : this(
        api,
        logger,
        { asset -> readPublishedVersion(asset, client, json) },
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private var flight: Pair<InstalledAppVersion, Deferred<AppResult<UpdateCheck>>>? = null
    private var blockedUntil = 0L

    override suspend fun checkForUpdates(current: InstalledAppVersion): AppResult<UpdateCheck> {
        val job =
            gate.withLock {
                flight?.takeIf { it.first == current && !it.second.isCompleted }?.second ?: scope
                    .async {
                        resolve(current)
                    }.also { flight = current to it }
            }
        return job.await()
    }

    private suspend fun resolve(current: InstalledAppVersion): AppResult<UpdateCheck> =
        try {
            withTimeout(30_000) {
                if (now() <
                    blockedUntil
                ) {
                    return@withTimeout error("GitHub rate limit reached. Retry after ${Instant.ofEpochMilli(blockedUntil)}.", 429)
                }
                val response = api.getLatestRelease()
                if (!response.isSuccessful) {
                    val limited =
                        response.code() == 429 ||
                            (
                                response.code() == 403 &&
                                    (response.headers()["X-RateLimit-Remaining"] == "0" || response.headers()["Retry-After"] != null)
                            )
                    return@withTimeout when {
                        limited -> {
                            blockedUntil = response.headers()["X-RateLimit-Reset"]?.toLongOrNull()?.times(1000)
                                ?: (now() + (response.headers()["Retry-After"]?.toLongOrNull() ?: 60).coerceAtLeast(1) * 1000)
                            error("GitHub rate limit reached. Retry after ${Instant.ofEpochMilli(blockedUntil)}.", response.code())
                        }
                        response.code() == 404 ->
                            error(
                                "No public published release is available. The repository may be private or have no releases. GitHub Actions artifacts are not Releases.",
                                404,
                            )
                        else -> error("GitHub update check failed (HTTP ${response.code()}). Please retry.", response.code())
                    }
                }
                val release = response.body() ?: return@withTimeout error("GitHub returned an empty release response.")
                if (release.draft ||
                    release.prerelease ||
                    release.publishedAt.isNullOrBlank()
                ) {
                    return@withTimeout error("No published stable release is available.")
                }
                val latest =
                    UpdateVersion.parse(release.tagName)
                        ?: return@withTimeout error("The release has an invalid version tag: ${release.tagName}.")
                if (latest.prerelease.isNotEmpty()) {
                    return@withTimeout error("The latest release tag is a prerelease; no stable update is available.")
                }
                val installed = UpdateVersion.parse(current.versionName)
                if (installed != null && latest < installed) return@withTimeout AppResult.Success(UpdateCheck.UpToDate)
                val versionAsset = release.assets.firstOrNull { it.name == "verification.json" && trustedReleaseAsset(it) }
                val declared = versionAsset?.let { manifest(it) }
                if (declared != null &&
                    (declared.packageName != current.packageName || UpdateVersion.parse(declared.versionName) != latest)
                ) {
                    return@withTimeout error("The release version metadata does not match this application.")
                }
                if (declared != null &&
                    declared.versionCode <= current.versionCode
                ) {
                    return@withTimeout AppResult.Success(UpdateCheck.UpToDate)
                }
                if (installed == null &&
                    declared == null
                ) {
                    return@withTimeout error(
                        "Cannot compare this release with the installed version. The release needs verification.json version metadata.",
                    )
                }
                if (installed != null && latest == installed && declared == null) return@withTimeout AppResult.Success(UpdateCheck.UpToDate)
                val asset =
                    selectReleaseApk(release.assets, current.abis)
                        ?: return@withTimeout error(
                            "This release has no compatible signed APK for this device (${current.abis.joinToString()}).",
                        )
                AppResult.Success(UpdateCheck.Available(release, asset, declared?.versionCode))
            }
        } catch (e: TimeoutCancellationException) {
            error("The GitHub update check timed out. Check your connection and retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w("UpdateChecker", "GitHub update request failed", e)
            error(
                if (e is IOException) {
                    "Cannot reach GitHub. Check your internet connection and retry."
                } else {
                    "GitHub returned an invalid update response. Please retry."
                },
            )
        }

    private fun error(
        message: String,
        code: Int? = null,
    ): AppResult.Error = AppResult.Error(AppError.NetworkError(message, code))
}

private suspend fun readPublishedVersion(
    asset: GitHubAssetDto,
    client: OkHttpClient,
    json: Json,
): PublishedAppVersion? =
    withContext(Dispatchers.IO) {
        require(asset.size <= 65_536) { "Release version metadata is too large" }
        client
            .newBuilder()
            .callTimeout(
                20,
                java.util.concurrent.TimeUnit.SECONDS,
            ).build()
            .newCall(
                Request
                    .Builder()
                    .url(asset.browserDownloadUrl)
                    .header("User-Agent", "erhu-update-checker")
                    .build(),
            ).execute()
            .use { response ->
                if (!response.isSuccessful) throw IOException("Release version metadata unavailable")
                val body = response.body ?: throw IOException("Empty version metadata")
                val bytes =
                    body.byteStream().use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 65_536) { "Release version metadata is too large" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                val obj = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                PublishedAppVersion(
                    obj.getValue("applicationId").jsonPrimitive.content,
                    obj.getValue("versionName").jsonPrimitive.content,
                    obj.getValue("versionCode").jsonPrimitive.long,
                )
            }
    }
