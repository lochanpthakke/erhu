package com.lochanpthakke.empee3.core.network.apple.model

import kotlinx.serialization.Serializable

@Serializable
data class AppleCatalogResponse(
    val data: List<AppleCatalogData> = emptyList(),
)

@Serializable
data class AppleCatalogData(
    val attributes: AppleCatalogAttributes? = null,
)

@Serializable
data class AppleCatalogAttributes(
    val editorialVideo: AppleEditorialVideo? = null,
)

@Serializable
data class AppleEditorialVideo(
    val motionDetailSquare: AppleMotionDetail? = null,
    val motionDetailTall: AppleMotionDetail? = null,
    val motionSquareVideo1x1: AppleMotionDetail? = null,
    val motionTallVideo3x4: AppleMotionDetail? = null,
)

@Serializable
data class AppleMotionDetail(
    val video: String? = null,
)
