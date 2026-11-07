package com.erhu.erhu.core.database.dao
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erhu.erhu.core.database.ErhuDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk=[34])
class VybeAliasQueryTest {
 private val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),ErhuDatabase::class.java)
  .allowMainThreadQueries().build()
 @After fun close()=db.close()
 @Test fun approvedAliasProjectionReadsChangesWithoutAffectingLibraryIdentity()=runBlocking {
  assertEquals(emptyList<String>(),db.artistDao().observeAliases().first().map{it.aliasKey})
  db.openHelper.writableDatabase.execSQL(
   "INSERT INTO artist_alias(alias_key,canonical_key,source,created_at) VALUES(?,?,?,?)",
   arrayOf<Any>("mr ocean","frank ocean","MANUAL",1L))
  val row=db.artistDao().observeAliases().first().single()
  assertEquals("mr ocean",row.aliasKey)
  assertEquals("frank ocean",row.canonicalKey)
  db.openHelper.writableDatabase.execSQL("UPDATE artist_alias SET canonical_key=? WHERE alias_key=?",arrayOf<Any>("other artist","mr ocean"))
  assertEquals("other artist",db.artistDao().observeAliases().first().single().canonicalKey)
 }
}
