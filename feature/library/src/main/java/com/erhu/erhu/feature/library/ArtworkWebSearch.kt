package com.erhu.erhu.feature.library

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.erhu.erhu.core.common.locale.AppLocaleManager
import java.net.URLEncoder
import com.erhu.erhu.core.common.R as CommonR

/** Manual browser fallback only: never used as an artwork provider or an automatic download. */
internal fun albumArtworkSearchUrl(
    title: String,
    artist: String,
): String? =
    listOf(title.trim(), artist.trim())
        .filter(String::isNotBlank)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")
        ?.let { googleImageSearchUrl("$it album cover image") }

internal fun artistArtworkSearchUrl(name: String): String? =
    name.trim().takeIf(String::isNotEmpty)?.let { googleImageSearchUrl("$it artist photo") }

private fun googleImageSearchUrl(query: String): String = "https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}&tbm=isch"

@Composable
internal fun rememberArtworkWebSearchLauncher(): (String) -> Unit {
    val handler = LocalUriHandler.current
    val context = LocalContext.current
    return remember(handler, context) {
        { url ->
            try {
                handler.openUri(url)
            } catch (_: IllegalArgumentException) {
                val localized = AppLocaleManager.localizedContext(context)
                Toast.makeText(context, localized.getString(CommonR.string.artwork_web_no_browser), Toast.LENGTH_LONG).show()
            } catch (_: SecurityException) {
                val localized = AppLocaleManager.localizedContext(context)
                Toast.makeText(context, localized.getString(CommonR.string.artwork_web_browser_blocked), Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
internal fun ArtworkWebSearchButton(
    url: String?,
    targetName: String,
    modifier: Modifier = Modifier,
) {
    if (url == null) return
    val open = rememberArtworkWebSearchLauncher()
    val searchCd = stringResource(CommonR.string.artwork_web_search_cd, targetName)
    TextButton(
        onClick = { open(url) },
        modifier = modifier.semantics { contentDescription = searchCd },
    ) {
        Text(stringResource(CommonR.string.artwork_web_search_button))
    }
}
