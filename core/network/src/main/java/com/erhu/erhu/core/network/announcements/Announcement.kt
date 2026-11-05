package com.erhu.erhu.core.network.announcements

import com.erhu.erhu.core.network.update.InstalledAppVersion
import com.erhu.erhu.core.network.update.UpdateVersion
import kotlinx.serialization.Serializable
import java.net.URI
import java.time.Instant

@Serializable
data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val publishedAt: String,
    val expiresAt: String,
    val imageUrl: String? = null,
    val actionLabel: String = "",
    val actionUrl: String? = null,
    val minVersionCode: Long? = null,
    val maxVersionCode: Long? = null,
    val minVersionName: String? = null,
    val maxVersionName: String? = null,
    val active: Boolean = true,
) {
    fun valid(): Boolean =
        runCatching {
            validAnnouncementId(id) &&
                title.isNotBlank() &&
                title.length <= 120 &&
                body.isNotBlank() &&
                body.length <= 4000 &&
                type in setOf("general", "feature", "important_update", "maintenance") &&
                Instant.parse(publishedAt).isBefore(Instant.parse(expiresAt)) &&
                (imageUrl == null || safeAnnouncementUrl(imageUrl)) &&
                (actionUrl == null || safeAnnouncementUrl(actionUrl)) &&
                actionLabel.length <= 60 &&
                (actionLabel.isNotBlank() == (actionUrl != null)) &&
                (minVersionCode == null || minVersionCode > 0) &&
                (maxVersionCode == null || maxVersionCode > 0) &&
                (minVersionCode == null || maxVersionCode == null || minVersionCode <= maxVersionCode) &&
                (minVersionName == null || (minVersionName.length <= 80 && UpdateVersion.parse(minVersionName) != null)) &&
                (maxVersionName == null || (maxVersionName.length <= 80 && UpdateVersion.parse(maxVersionName) != null)) &&
                (
                    minVersionName == null ||
                        maxVersionName == null ||
                        UpdateVersion.parse(minVersionName)!! <= UpdateVersion.parse(maxVersionName)!!
                )
        }.getOrDefault(false)

    fun eligible(
        version: InstalledAppVersion,
        now: Long,
    ): Boolean =
        valid() &&
            active &&
            now >= Instant.parse(publishedAt).toEpochMilli() &&
            now < Instant.parse(expiresAt).toEpochMilli() &&
            (minVersionCode == null || version.versionCode >= minVersionCode) &&
            (maxVersionCode == null || version.versionCode <= maxVersionCode) &&
            runCatching {
                val installed = UpdateVersion.parse(version.versionName)
                (minVersionName == null || (installed != null && installed >= UpdateVersion.parse(minVersionName)!!)) &&
                    (maxVersionName == null || (installed != null && installed <= UpdateVersion.parse(maxVersionName)!!))
            }.getOrDefault(false)
}

@Serializable
data class AnnouncementFeed(
    val announcements: List<Announcement> = emptyList(),
)

fun validAnnouncementId(id: String): Boolean = Regex("[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}").matches(id)

fun safeAnnouncementUrl(value: String): Boolean =
    runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase().orEmpty()
        value.length <= 2048 &&
            uri.scheme == "https" &&
            host.isNotEmpty() &&
            uri.userInfo == null &&
            uri.port == -1 &&
            host != "localhost" &&
            !host.endsWith(".local") &&
            !Regex("[0-9.:\\[\\]]+").matches(host)
    }.getOrDefault(false)
