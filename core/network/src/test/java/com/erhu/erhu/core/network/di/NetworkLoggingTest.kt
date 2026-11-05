package com.erhu.erhu.core.network.di

import org.junit.Assert.assertEquals
import org.junit.Test

/** Shared log redaction still protects unrelated provider integrations after metadata-provider removal. */
class NetworkLoggingTest {
    @Test fun queryCredentialsAreRedactedWhileOrdinarySearchTermsAreRetained() {
        val line = "GET https://example.test/search?term=title&api_key=test-value&apikey=other-value&token=test-token&secret=test-secret"
        assertEquals(
            "GET https://example.test/search?term=title&api_key=<redacted>&apikey=<redacted>&token=<redacted>&secret=<redacted>",
            redactSecrets(line),
        )
    }
    @Test fun audioDbPathKeyIsRedacted() {
        assertEquals("GET https://www.theaudiodb.com/api/v1/json/<redacted>/searchalbum.php?a=Discovery", redactSecrets("GET https://www.theaudiodb.com/api/v1/json/private-key/searchalbum.php?a=Discovery"))
    }
}
