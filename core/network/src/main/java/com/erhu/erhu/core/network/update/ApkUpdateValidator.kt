package com.erhu.erhu.core.network.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject

/** Testable installation policy; Android's installer performs the final cryptographic/install checks. */
data class ApkIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val abis: Set<String>,
    val signers: Set<String>,
    val signingHistory: Set<String>,
    val debuggable: Boolean = false,
    val testOnly: Boolean = false,
)

fun validateUpdateIdentity(
    installed: ApkIdentity,
    downloaded: ApkIdentity,
    update: UpdateCheck.Available,
    sdk: Int,
    abis: List<String>,
) {
    require(downloaded.packageName == installed.packageName) { "The downloaded APK belongs to another application." }
    require(downloaded.versionCode > installed.versionCode) { "This APK's versionCode is not newer than the installed app." }
    require(
        update.versionCode == null || downloaded.versionCode == update.versionCode,
    ) { "The APK versionCode does not match the release." }
    val publishedVersion = requireNotNull(UpdateVersion.parse(update.release.tagName)) { "The release tag is invalid." }
    require(UpdateVersion.parse(downloaded.versionName) == publishedVersion) {
        "The APK version does not match the release tag."
    }
    require(!downloaded.debuggable && !downloaded.testOnly) { "A debug or test-only APK cannot be installed as a release update." }
    require(downloaded.minSdk <= sdk) { "This APK requires a newer Android version." }
    require(downloaded.abis.isEmpty() || downloaded.abis.any { it in abis }) { "This APK does not support this device's CPU architecture." }
    require(installed.signers.isNotEmpty() && downloaded.signers.isNotEmpty()) { "Could not verify the APK signing certificate." }
    val compatibleSigner =
        downloaded.signers == installed.signers ||
            (installed.signers.size == 1 && downloaded.signers.size == 1 && downloaded.signingHistory.containsAll(installed.signers))
    require(compatibleSigner) { "The APK signing certificate is incompatible with this installation." }
}

class ApkUpdateValidator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @Suppress("DEPRECATION")
        fun validate(
            file: File,
            update: UpdateCheck.Available,
        ): ApkIdentity {
            require(file.isFile && file.length() > 0) { "The downloaded APK is missing or empty." }
            val nativeAbis =
                try {
                    ZipFile(file).use { zip ->
                        require(zip.getEntry("AndroidManifest.xml") != null && zip.getEntry("classes.dex") != null) {
                            "The download is not a valid application APK."
                        }
                        zip
                            .entries()
                            .asSequence()
                            .map { it.name }
                            .filter { it.startsWith("lib/") && it.endsWith(".so") }
                            .mapNotNull { it.split('/').getOrNull(1) }
                            .toSet()
                    }
                } catch (e: java.util.zip.ZipException) {
                    throw IllegalArgumentException("The download is not a valid APK or was damaged. Please retry.", e)
                }
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val installed = context.packageManager.getPackageInfo(context.packageName, flags)
            val archive =
                context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
                    ?: throw IllegalArgumentException("Android could not parse the APK. The download may be damaged.")
            val current = identity(installed, emptySet())
            val downloaded = identity(archive, nativeAbis)
            validateUpdateIdentity(current, downloaded, update, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList())
            return downloaded
        }

        @Suppress("DEPRECATION")
        private fun identity(
            info: PackageInfo,
            abis: Set<String>,
        ): ApkIdentity {
            val current = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            val history = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.signingCertificateHistory else current

            fun hashes(signatures: Array<android.content.pm.Signature>?) =
                signatures
                    .orEmpty()
                    .map {
                        MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
                    }.toSet()
            return ApkIdentity(
                info.packageName,
                info.versionName.orEmpty(),
                PackageInfoCompat.getLongVersionCode(info),
                info.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE,
                abis,
                hashes(current),
                hashes(history),
                (info.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
                (info.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_TEST_ONLY != 0,
            )
        }
    }
