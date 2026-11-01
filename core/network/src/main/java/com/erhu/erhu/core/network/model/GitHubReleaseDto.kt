@file:Suppress("detekt:All")

package com.erhu.erhu.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GitHubReleaseDto")
data class GitHubReleaseDto(
    @SerialName("tag_name")
    val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url")
    val htmlUrl: String = "",
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.model.GitHubAssetDto")
data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
)
