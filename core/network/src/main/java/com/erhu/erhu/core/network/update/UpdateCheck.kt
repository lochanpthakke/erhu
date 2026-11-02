package com.erhu.erhu.core.network.update

import com.erhu.erhu.core.network.api.UpdateRepositoryConfig
import com.erhu.erhu.core.network.model.GitHubAssetDto
import com.erhu.erhu.core.network.model.GitHubReleaseDto
import java.net.URI

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck

    data class Available(
        val release: GitHubReleaseDto,
        val asset: GitHubAssetDto,
        val versionCode: Long? = null,
    ) : UpdateCheck
}

data class PublishedAppVersion(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
)

/** Accept only assets actually attached to the configured repository's HTTPS release. */
fun trustedReleaseAsset(asset: GitHubAssetDto): Boolean =
    runCatching {
        val uri = URI(asset.browserDownloadUrl)
        uri.scheme == "https" &&
            uri.host == "github.com" &&
            uri.userInfo == null &&
            uri.path.startsWith("/${UpdateRepositoryConfig.OWNER}/${UpdateRepositoryConfig.REPOSITORY}/releases/download/")
    }.getOrDefault(false)

fun selectReleaseApk(
    assets: List<GitHubAssetDto>,
    abis: List<String>,
): GitHubAssetDto? {
    val known = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun abi(name: String): String? = known.firstOrNull { name.contains(it) || (it == "arm64-v8a" && name.contains("aarch64")) }
    val candidates =
        assets.filter {
            val name = it.name.lowercase()
            it.state == "uploaded" &&
                name.endsWith(".apk") &&
                trustedReleaseAsset(it) &&
                listOf("debug", "unsigned", "benchmark", ".perf").none(name::contains)
        }
    for (deviceAbi in abis) {
        candidates.firstOrNull { abi(it.name.lowercase()) == deviceAbi }?.let { return it }
    }
    val universal = candidates.filter { abi(it.name.lowercase()) == null }
    return universal.singleOrNull { it.name.contains("universal", true) } ?: universal.singleOrNull()
}
