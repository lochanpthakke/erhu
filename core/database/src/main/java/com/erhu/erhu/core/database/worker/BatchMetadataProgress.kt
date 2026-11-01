package com.erhu.erhu.core.database.worker

data class BatchMetadataProgress(
    val totalTracks: Int = 0,
    val processedCount: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val needsReviewCount: Int = 0,
    val currentTrackTitle: String? = null,
    val isComplete: Boolean = false,
)
