package com.erhu.erhu.feature.library

import androidx.annotation.StringRes
import com.erhu.erhu.core.common.R as CommonR

internal enum class UtilityTool { ARTWORK, TAGS, BUILDER }

/** Usage guidance for existing tools; these pages never enqueue repairs or modify music files. */
internal data class UtilityGuide(
    @StringRes val entryPointRes: Int,
    val steps: List<Pair<Int, Int>>,
    val details: List<Pair<Int, Int>>,
)

internal fun UtilityTool.toolGuide(): UtilityGuide =
    when (this) {
        UtilityTool.ARTWORK ->
            UtilityGuide(
                entryPointRes = CommonR.string.onboarding_guide_artwork_entry,
                steps =
                    listOf(
                        CommonR.string.onboarding_guide_artwork_step1_title to
                            CommonR.string.onboarding_guide_artwork_step1_body,
                        CommonR.string.onboarding_guide_artwork_step2_title to
                            CommonR.string.onboarding_guide_artwork_step2_body,
                        CommonR.string.onboarding_guide_artwork_step3_title to
                            CommonR.string.onboarding_guide_artwork_step3_body,
                    ),
                details =
                    listOf(
                        CommonR.string.onboarding_guide_artwork_detail1_title to
                            CommonR.string.onboarding_guide_artwork_detail1_body,
                        CommonR.string.onboarding_guide_artwork_detail2_title to
                            CommonR.string.onboarding_guide_artwork_detail2_body,
                        CommonR.string.onboarding_guide_artwork_detail3_title to
                            CommonR.string.onboarding_guide_artwork_detail3_body,
                    ),
            )
        UtilityTool.TAGS ->
            UtilityGuide(
                entryPointRes = CommonR.string.onboarding_guide_tags_entry,
                steps =
                    listOf(
                        CommonR.string.onboarding_guide_tags_step1_title to
                            CommonR.string.onboarding_guide_tags_step1_body,
                        CommonR.string.onboarding_guide_tags_step2_title to
                            CommonR.string.onboarding_guide_tags_step2_body,
                        CommonR.string.onboarding_guide_tags_step3_title to
                            CommonR.string.onboarding_guide_tags_step3_body,
                    ),
                details =
                    listOf(
                        CommonR.string.onboarding_guide_tags_detail1_title to
                            CommonR.string.onboarding_guide_tags_detail1_body,
                        CommonR.string.onboarding_guide_tags_detail2_title to
                            CommonR.string.onboarding_guide_tags_detail2_body,
                        CommonR.string.onboarding_guide_tags_detail3_title to
                            CommonR.string.onboarding_guide_tags_detail3_body,
                    ),
            )
        UtilityTool.BUILDER ->
            UtilityGuide(
                entryPointRes = CommonR.string.onboarding_guide_builder_entry,
                steps =
                    listOf(
                        CommonR.string.onboarding_guide_builder_step1_title to
                            CommonR.string.onboarding_guide_builder_step1_body,
                        CommonR.string.onboarding_guide_builder_step2_title to
                            CommonR.string.onboarding_guide_builder_step2_body,
                        CommonR.string.onboarding_guide_builder_step3_title to
                            CommonR.string.onboarding_guide_builder_step3_body,
                    ),
                details =
                    listOf(
                        CommonR.string.onboarding_guide_builder_detail0_title to
                            CommonR.string.onboarding_guide_builder_detail0_body,
                        CommonR.string.onboarding_guide_builder_detail1_title to
                            CommonR.string.onboarding_guide_builder_detail1_body,
                        CommonR.string.onboarding_guide_builder_detail2_title to
                            CommonR.string.onboarding_guide_builder_detail2_body,
                    ),
            )
    }
