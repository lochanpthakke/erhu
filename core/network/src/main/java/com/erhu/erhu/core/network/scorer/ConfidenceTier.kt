package com.erhu.erhu.core.network.scorer

const val HIGH_CONFIDENCE_THRESHOLD = 0.85f
const val UNCERTAIN_THRESHOLD = 0.60f

enum class ConfidenceTier {
    HIGH_CONFIDENCE,
    UNCERTAIN,
    NO_MATCH,
    NETWORK_ERROR,
}
