package com.erhu.erhu.core.database

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Open a populated legacy v14 file through the renamed Room class without replacing its data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdentityRenameCompatibilityTest {
    @Test
    fun renamedDatabaseOpensExistingSchemaAndRetainsEveryTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schema = context.assets.open("com.erhu.erhu.core.database.ErhuDatabase/14.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
        assertEquals("9f1571a03bdddc922b49ac6c9aeac1dc", schema.getString("identityHash"))
        val entities = schema.getJSONArray("entities")
        val names = (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
        val fileName = "empee3.db"
        context.deleteDatabase(fileName)
        val before = context.openOrCreateDatabase(fileName, Context.MODE_PRIVATE, null).use { legacy ->
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (i in 0 until indices.length()) legacy.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                val fields = entity.getJSONArray("fields")
                val columns = (0 until fields.length()).map { fields.getJSONObject(it) }
                val values = columns.map { field ->
                    when (field.getString("affinity")) {
                        "INTEGER" -> 42L
                        "REAL" -> 0.5
                        "BLOB" -> byteArrayOf(1, 2, 3)
                        else -> "retained-$table-${field.getString("columnName")}"
                    }
                }
                val columnSql = columns.joinToString(",") { "`${it.getString("columnName")}`" }
                legacy.execSQL("INSERT INTO `$table` ($columnSql) VALUES (${columns.joinToString(",") { "?" }})", values.toTypedArray())
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) legacy.execSQL(setup.getString(i))
            legacy.version = 14
            names.associateWith { table -> legacy.rawQuery("SELECT * FROM `$table`", null).use(::rows) }
        }
        val renamed = Room.databaseBuilder(context, ErhuDatabase::class.java, fileName).allowMainThreadQueries().build()
        try {
            val db = renamed.openHelper.readableDatabase
            val after = names.associateWith { table -> db.query("SELECT * FROM `$table`").use(::rows) }
            assertEquals(before, after)
            db.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use {
                it.moveToFirst()
                assertEquals(schema.getString("identityHash"), it.getString(0))
            }
        } finally {
            renamed.close()
            context.deleteDatabase(fileName)
        }
    }

    private fun rows(cursor: Cursor): List<List<String?>> = buildList {
        while (cursor.moveToNext()) add((0 until cursor.columnCount).map {
            if (cursor.getType(it) == Cursor.FIELD_TYPE_BLOB) cursor.getBlob(it).joinToString() else cursor.getString(it)
        })
    }
}
