package com.erhu.erhu

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.erhu.erhu.announcements.AnnouncementMemory
import com.erhu.erhu.announcements.AnnouncementStore
import com.erhu.erhu.core.network.announcements.Announcement
import com.erhu.erhu.core.network.update.InstalledAppVersion
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AnnouncementPersistenceTest {
    @Test fun readDismissedAndNotifiedIdsSurviveStoreRecreation() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Isolated benchmark package only",context.packageName.endsWith(".bench"))
        val file=File(context.cacheDir,"announcement-${UUID.randomUUID()}.preferences_pb")
        val firstScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val secondScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val id=UUID.randomUUID().toString()
        val now=System.currentTimeMillis()
        val item=Announcement(id,"Isolated test notice","No push message was sent.","general",Instant.ofEpochMilli(now-1000).toString(),Instant.ofEpochMilli(now+60000).toString())
        try {
            val first=AnnouncementStore(PreferenceDataStoreFactory.create(scope=firstScope){file})
            val expected=AnnouncementMemory(listOf(item),read=setOf(id),dismissed=setOf(id),notified=setOf(id),lastSync=now)
            first.update{expected}
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            val recreated=AnnouncementStore(PreferenceDataStoreFactory.create(scope=secondScope){file})
            val actual=recreated.data.first()
            assertEquals(expected,actual)
            assertTrue(item.eligible(InstalledAppVersion.read(context),now))
            assertTrue(actual.items.filter{it.id !in actual.read && it.id !in actual.dismissed}.isEmpty())
            assertTrue(id in actual.notified)
        } finally {
            firstScope.coroutineContext[Job]!!.cancelAndJoin();secondScope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
