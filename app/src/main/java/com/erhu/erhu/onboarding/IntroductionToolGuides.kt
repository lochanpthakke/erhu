package com.erhu.erhu.onboarding

import androidx.annotation.StringRes
import com.erhu.erhu.core.common.R as CommonR

/** Usage guidance for existing tools; these pages never enqueue repairs or modify music files. */
internal data class IntroductionToolGuide(
    @StringRes val entryPointRes: Int,
    val steps: List<Pair<Int, Int>>,
    val details: List<Pair<Int, Int>>,
)

internal fun IntroductionPage.toolGuide(): IntroductionToolGuide? =
    when (this) {
        IntroductionPage.ARTWORK ->
            IntroductionToolGuide(
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
        IntroductionPage.TAGS ->
            IntroductionToolGuide(
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
        IntroductionPage.BUILDER ->
            IntroductionToolGuide(
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
        IntroductionPage.TRANSITIONS ->
            IntroductionToolGuide(
                entryPointRes = CommonR.string.onboarding_guide_transitions_entry,
                steps =
                    listOf(
                        CommonR.string.onboarding_guide_transitions_step1_title to
                            CommonR.string.onboarding_guide_transitions_step1_body,
                        CommonR.string.onboarding_guide_transitions_step2_title to
                            CommonR.string.onboarding_guide_transitions_step2_body,
                        CommonR.string.onboarding_guide_transitions_step3_title to
                            CommonR.string.onboarding_guide_transitions_step3_body,
                    ),
                details =
                    listOf(
                        CommonR.string.onboarding_guide_transitions_detail1_title to
                            CommonR.string.onboarding_guide_transitions_detail1_body,
                        CommonR.string.onboarding_guide_transitions_detail2_title to
                            CommonR.string.onboarding_guide_transitions_detail2_body,
                        CommonR.string.onboarding_guide_transitions_detail3_title to
                            CommonR.string.onboarding_guide_transitions_detail3_body,
                    ),
            )
        else -> null
    }
