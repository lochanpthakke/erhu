package com.erhu.erhu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.erhu.erhu.feature.library.playerArtworkLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerArtworkLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun heightLimitedArtworkReportsImageBoundsRatherThanWideContainer() {
        var outer = Rect.Zero
        var target = Rect.Zero
        var image = Rect.Zero
        compose.setContent {
            Box(Modifier.size(width = 300.dp, height = 180.dp)) {
                Box(
                    Modifier
                        .onGloballyPositioned { outer = it.boundsInRoot() }
                        .playerArtworkLayout { target = it }
                        .onGloballyPositioned { image = it.boundsInRoot() },
                )
            }
        }
        compose.runOnIdle {
            assertTrue("Outer fillMaxWidth is wider than the height-limited image", outer.width > image.width)
            assertEquals(image, target)
            assertEquals(target.width, target.height, 0.01f)
        }
    }

    @Test
    fun parentClippingAndRevealDoNotChangeArtworkDestination() {
        val reveal = mutableFloatStateOf(0f)
        var target = Rect.Zero
        var clipped = Rect.Zero
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.size(200.dp).clipToBounds().graphicsLayer { alpha = reveal.floatValue }) {
                    Box(
                        Modifier
                            .offset(y = 100.dp)
                            .size(180.dp)
                            .playerArtworkLayout { target = it }
                            .onGloballyPositioned { clipped = it.boundsInRoot() },
                    )
                }
            }
        }
        lateinit var destination: Rect
        compose.runOnIdle {
            destination = target
            assertTrue("Clipped visible bounds cannot be the destination", clipped.height < target.height)
            assertEquals(target.width, target.height, 0.01f)
            reveal.floatValue = 1f
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(destination, target) }
    }
}
