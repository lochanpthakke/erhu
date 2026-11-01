package com.erhu.erhu.core.network.tagger

import com.erhu.erhu.core.common.logging.Logger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddedTagReaderTest {
    private val logger = object : Logger {
        override fun v(tag: String, message: String, throwable: Throwable?) {}
        override fun d(tag: String, message: String, throwable: Throwable?) {}
        override fun i(tag: String, message: String, throwable: Throwable?) {}
        override fun w(tag: String, message: String, throwable: Throwable?) {}
        override fun e(tag: String, message: String, throwable: Throwable?) {}
    }
    private val tagReader = EmbeddedTagReader(logger)

    @Test
    fun nonExistentFile_returnsHasEmbeddedTagFalse() = runBlocking {
        val result = tagReader.readEmbeddedTags("/no_such_directory/non_existent_track.mp3")
        assertFalse(result.hasEmbeddedTag)
        assertNull(result.title)
        assertNull(result.artist)
    }
}
