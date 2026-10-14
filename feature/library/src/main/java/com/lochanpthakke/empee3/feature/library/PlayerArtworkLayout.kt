package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.lochanpthakke.empee3.core.designsystem.foundation.ArtworkSize

/** Measure the rendered square, after height constraints have resolved its size. */
fun Modifier.playerArtworkLayout(onBounds: (Rect) -> Unit): Modifier =
    widthIn(max = ArtworkSize.heroMax)
        .fillMaxWidth()
        .aspectRatio(1f, matchHeightConstraintsFirst = true)
        .onGloballyPositioned { coordinates ->
            // The expanding surface clips its children while revealing them.
            // Its visible area must not become the artwork's destination bounds.
            onBounds(coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false))
        }
