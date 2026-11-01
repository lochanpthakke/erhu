@file:Suppress("detekt:All")

package com.erhu.erhu.core.database.scanner

data class ScanProgress(
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentSongTitle: String? = null,
    val isComplete: Boolean = false,
)
