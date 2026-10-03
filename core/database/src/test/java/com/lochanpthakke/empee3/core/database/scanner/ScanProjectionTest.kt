package com.lochanpthakke.empee3.core.database.scanner

import android.os.Build
import android.provider.MediaStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review finding 1: the compilation flag must actually be requested from MediaStore (API 30+). */
class ScanProjectionTest {
    @Test
    fun `album artist and compilation are requested on API 30 and later`() {
        val projection = scanProjection(Build.VERSION_CODES.R).toList()
        assertTrue(MediaStore.Audio.Media.ALBUM_ARTIST in projection)
        assertTrue(MediaStore.Audio.Media.COMPILATION in projection)
    }

    @Test
    fun `columns that do not exist before API 30 are not requested`() {
        val projection = scanProjection(Build.VERSION_CODES.Q).toList()
        assertFalse(MediaStore.Audio.Media.ALBUM_ARTIST in projection)
        assertFalse(MediaStore.Audio.Media.COMPILATION in projection)
        assertTrue(MediaStore.Audio.Media.TRACK in projection)
    }
}
