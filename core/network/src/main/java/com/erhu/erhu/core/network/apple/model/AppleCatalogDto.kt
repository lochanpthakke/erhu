package com.erhu.erhu.core.network.apple.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.apple.model.AppleCatalogResponse")
data class AppleCatalogResponse(
    val data: List<AppleCatalogData> = emptyList(),
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.apple.model.AppleCatalogData")
data class AppleCatalogData(
    val attributes: AppleCatalogAttributes? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.apple.model.AppleCatalogAttributes")
data class AppleCatalogAttributes(
    val editorialVideo: AppleEditorialVideo? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.apple.model.AppleEditorialVideo")
data class AppleEditorialVideo(
    val motionDetailSquare: AppleMotionDetail? = null,
    val motionDetailTall: AppleMotionDetail? = null,
    val motionSquareVideo1x1: AppleMotionDetail? = null,
    val motionTallVideo3x4: AppleMotionDetail? = null,
)

@Serializable
@SerialName("com.lochanpthakke.empee3.core.network.apple.model.AppleMotionDetail")
data class AppleMotionDetail(
    val video: String? = null,
)
