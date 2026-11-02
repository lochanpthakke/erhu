package com.erhu.erhu.core.network.update

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.math.BigInteger

/** Android installation identity, read from the installed package rather than a library BuildConfig. */
data class InstalledAppVersion(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val abis: List<String>,
) {
    companion object {
        fun read(context: Context): InstalledAppVersion {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return InstalledAppVersion(
                context.packageName,
                info.versionName.orEmpty(),
                PackageInfoCompat.getLongVersionCode(info),
                Build.SUPPORTED_ABIS.toList(),
            )
        }
    }
}

/** Numeric SemVer ordering, with optional v/erhu-v tags and missing patch components. */
data class UpdateVersion(
    val numbers: List<BigInteger>,
    val prerelease: List<String>,
) : Comparable<UpdateVersion> {
    override fun compareTo(other: UpdateVersion): Int {
        for (i in 0..2) {
            val comparison = numbers[i].compareTo(other.numbers[i])
            if (comparison != 0) return comparison
        }
        if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
        if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1
        for (i in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val a = prerelease[i]
            val b = other.prerelease[i]
            val an = a.toBigIntegerOrNull()
            val bn = b.toBigIntegerOrNull()
            val comparison =
                when {
                    an != null && bn != null -> an.compareTo(bn)
                    an != null -> -1
                    bn != null -> 1
                    else -> a.compareTo(b)
                }
            if (comparison != 0) return comparison
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        fun parse(raw: String): UpdateVersion? {
            val tag =
                raw
                    .trim()
                    .removePrefix("erhu-")
                    .removePrefix("v")
                    .removePrefix("V")
            val m =
                Regex(
                    """(0|[1-9]\d*)(?:\.(0|[1-9]\d*))?(?:\.(0|[1-9]\d*))?(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?""",
                ).matchEntire(tag)
                    ?: return null
            val pre =
                m.groupValues[4]
                    .takeIf(String::isNotEmpty)
                    ?.split('.')
                    .orEmpty()
            if (pre.any { it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') }) return null
            return UpdateVersion((1..3).map { m.groupValues[it].ifEmpty { "0" }.toBigInteger() }, pre)
        }
    }
}
