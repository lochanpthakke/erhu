package com.erhu.erhu.core.network.announcements

import com.erhu.erhu.core.network.update.InstalledAppVersion
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AnnouncementTest {
    private val now = Instant.parse("2026-11-04T12:00:00Z").toEpochMilli()
    private val installed = InstalledAppVersion("com.lochanpthakke.empee3", "1.0.3",4,listOf("arm64-v8a"))
    private val item = Announcement("11111111-1111-1111-1111-111111111111","New feature","A notice","feature","2026-11-04T11:00:00Z","2026-11-05T12:00:00Z")

    @Test fun scheduledExpiredInactiveAndVersionRestrictedNoticesAreNotEligible() {
        assertTrue(item.eligible(installed,now))
        assertFalse(item.copy(publishedAt="2026-11-04T13:00:00Z").eligible(installed,now))
        assertFalse(item.eligible(installed,Instant.parse(item.expiresAt).toEpochMilli()))
        assertFalse(item.copy(active=false).eligible(installed,now))
        assertFalse(item.copy(minVersionCode=5).eligible(installed,now))
        assertFalse(item.copy(maxVersionCode=3).eligible(installed,now))
        assertFalse(item.copy(minVersionName="1.0.4").eligible(installed,now))
        assertFalse(item.copy(maxVersionName="1.0.2").eligible(installed,now))
        assertTrue(item.copy(minVersionName="1.0.3-beta.1",maxVersionName="1.10.0").eligible(installed,now))
    }
    @Test fun malformedIdsUnsafeLinksAndInvalidRangesAreRejected() {
        for (bad in listOf(item.copy(id="../../bad"),item.copy(title=""),item.copy(body="x".repeat(4001)),item.copy(type="other"),item.copy(expiresAt="bad"),item.copy(minVersionCode=8,maxVersionCode=4),item.copy(minVersionName="1.1.0",maxVersionName="1.0.0"),item.copy(actionUrl="https://example.com/"),item.copy(actionLabel="Read",actionUrl="intent://unsafe"))) assertFalse(bad.valid())
        for(url in listOf("http://example.com/","file:///tmp/test","https://user:password@example.com/","https://localhost/","https://127.0.0.1/","https://[::1]/"))assertFalse(url,safeAnnouncementUrl(url))
        assertTrue(safeAnnouncementUrl("https://github.com/lochanpthakke/erhu/releases"))
    }
    @Test fun publicClientSkipsMalformedItemsAndFetchesOnlyValidatedIds() = runBlocking {
        MockWebServer().use { server ->
            val json=Json{ignoreUnknownKeys=true}
            val encoded=json.encodeToString(item)
            server.enqueue(MockResponse().setBody("{\"announcements\":[$encoded,{\"id\":\"malformed\"},$encoded]}"))
            server.enqueue(MockResponse().setResponseCode(404))
            val client=AnnouncementClient(OkHttpClient(),json,server.url("/api").toString(), insecureForTests=true)
            assertEquals(listOf(item),client.fetch())
            val request=server.takeRequest();assertEquals("/api/feed",request.path);assertNull(request.getHeader("Authorization"))
            assertTrue(client.fetch(item.id).isEmpty())
            assertEquals("/api/announcements/"+item.id,server.takeRequest().path)
            try {client.fetch("bad/id");fail("Invalid IDs must not trigger requests")}catch(_:IllegalArgumentException){}
            assertEquals(2,server.requestCount)
        }
    }
    @Test fun oversizedResponsesAndServerFailureStayErrorsNotSuccessfulEmptyFeeds() = runBlocking {
        MockWebServer().use {server ->
            val client=AnnouncementClient(OkHttpClient(),Json,server.url("/").toString(), insecureForTests=true)
            server.enqueue(MockResponse().setResponseCode(503))
            try{client.fetch();fail("Outage must remain visible")}catch(_:java.io.IOException){}
            server.enqueue(MockResponse().setBody("x".repeat(524289)))
            try{client.fetch();fail("Oversized response")}catch(_:IllegalArgumentException){}
        }
    }
}
