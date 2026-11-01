package com.erhu.erhu.core.vybe.mediapipe

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs without the model: loading problems surface as [VybeEmbedderException]. */
@RunWith(AndroidJUnit4::class)
class MediaPipeTextEmbedderErrorsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aMissingModelFileIsAClearError() {
        val missing = File(context.cacheDir, "no-such-model.task")
        assertTrue(runCatching { MediaPipeTextEmbedder.fromFile(context, missing) }.exceptionOrNull() is VybeEmbedderException)
    }

    @Test
    fun aFileThatIsNotAModelIsAClearError() {
        val notAModel = File(context.cacheDir, "not-a-model.task").apply { writeText("not a model") }
        try {
            assertTrue(runCatching { MediaPipeTextEmbedder.fromFile(context, notAModel) }.exceptionOrNull() is VybeEmbedderException)
        } finally {
            notAModel.delete()
        }
    }
}
