package com.erhu.erhu.core.network.remote.drive

import com.erhu.erhu.core.network.remote.subsonic.md5Hex
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DriveMetadataReaderCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var persistentDir: File
    private lateinit var legacyDir: File
    private lateinit var reader: DriveMetadataReader

    @Before
    fun setUp() {
        persistentDir = tmp.newFolder("files-remote-art")
        legacyDir = tmp.newFolder("cache-remote-art")
        val tokens =
            object : DriveTokenProvider {
                override fun token(sourceId: Long) = "unused"

                override fun invalidate(sourceId: Long) = Unit
            }
        reader =
            DriveMetadataReader(
                covers = persistentDir,
                legacyCovers = legacyDir,
                tokens = tokens,
                client = DriveClient(OkHttpClient(), "https://example.com/".toHttpUrl()),
            )
    }

    @Test
    fun `hasCachedCover and cachedCover detect persistent files including empty no-cover markers`() {
        val artBytes = byteArrayOf(1, 2, 3, 4)
        coverFile(persistentDir, 1L, "file-art").writeBytes(artBytes)
        coverFile(persistentDir, 1L, "file-empty").writeBytes(ByteArray(0))

        assertTrue(reader.hasCachedCover(1L, "file-art"))
        assertArrayEquals(artBytes, reader.cachedCover(1L, "file-art"))

        assertTrue(reader.hasCachedCover(1L, "file-empty"))
        assertArrayEquals(ByteArray(0), reader.cachedCover(1L, "file-empty"))

        assertFalse(reader.hasCachedCover(1L, "missing"))
        assertNull(reader.cachedCover(1L, "missing"))
    }

    @Test
    fun `legacy covers in cacheDir migrate automatically into persistent filesDir`() {
        val art1 = byteArrayOf(10, 20, 30)
        val art2 = byteArrayOf(40, 50)
        val legacy1 = coverFile(legacyDir, 2L, "f1").apply { writeBytes(art1) }
        val legacy2 = coverFile(legacyDir, 2L, "f2").apply { writeBytes(art2) }

        assertTrue(reader.hasCachedCover(2L, "f1"))
        assertTrue(coverFile(persistentDir, 2L, "f1").isFile)
        assertFalse(legacy1.exists())
        assertArrayEquals(art1, reader.cachedCover(2L, "f1"))

        assertArrayEquals(art2, reader.cachedCover(2L, "f2"))
        assertTrue(coverFile(persistentDir, 2L, "f2").isFile)
        assertFalse(legacy2.exists())
    }

    @Test
    fun `forget deletes both persistent and legacy covers only for the given source`() {
        coverFile(persistentDir, 3L, "a").writeBytes(byteArrayOf(1))
        coverFile(legacyDir, 3L, "b").writeBytes(byteArrayOf(2))
        coverFile(persistentDir, 4L, "c").writeBytes(byteArrayOf(3))
        coverFile(legacyDir, 4L, "d").writeBytes(byteArrayOf(4))

        reader.forget(3L)

        assertFalse(reader.hasCachedCover(3L, "a"))
        assertFalse(reader.hasCachedCover(3L, "b"))
        assertTrue(reader.hasCachedCover(4L, "c"))
        assertTrue(reader.hasCachedCover(4L, "d"))
    }

    private fun coverFile(
        dir: File,
        sourceId: Long,
        fileId: String,
    ) = File(dir, "drive-$sourceId-${md5Hex(fileId)}")
}
