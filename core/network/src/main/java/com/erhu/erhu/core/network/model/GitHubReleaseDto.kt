@file:Suppress("detekt:All")

package com.erhu.erhu.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GitHubReleaseDto")
data class GitHubReleaseDto(
    @SerialName("tag_name")
    val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url")
    val htmlUrl: String = "",
    val assets: List<GitHubAssetDto> = emptyList(),
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GitHubAssetDto")
data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
    val id: Long = 0,
    val size: Long = 0,
    val state: String = "uploaded",
    val digest: String? = null,
)
