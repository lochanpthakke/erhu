package com.lochanpthakke.empee3.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * v13 -> v14 adds the animated_artwork table.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration13To14Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EmPee3Database::class.java)

    @Test
    fun `animated artwork table is created`() {
        helper.createDatabase(DB, 13).close()

        helper.runMigrationsAndValidate(DB, 14, true, MIGRATION_13_14).use { db ->
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='animated_artwork'")
            assertTrue(cursor.moveToFirst())
            cursor.close()
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
