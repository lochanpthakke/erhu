package com.erhu.erhu.core.network.di

import com.erhu.erhu.core.network.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Transport gate for optional integrations without established distribution permission. */
internal object DistributionNetworkPolicy : Interceptor {
    private val restrictedDomains = setOf(
        "apple.com", "mzstatic.com", "deezer.com", "dzcdn.net", "musixmatch.com",
        "theaudiodb.com", "musicbrainz.org", "coverartarchive.org", "lrclib.net",
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        if (BuildConfig.PROPRIETARY_CANDIDATE && restrictedDomains.any { host == it || host.endsWith(".$it") }) {
            throw IOException("This optional online provider is unavailable in this distribution pending permission.")
        }
        return chain.proceed(chain.request())
    }
}
