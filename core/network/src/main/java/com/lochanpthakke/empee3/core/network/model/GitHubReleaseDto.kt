@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
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
data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
)
