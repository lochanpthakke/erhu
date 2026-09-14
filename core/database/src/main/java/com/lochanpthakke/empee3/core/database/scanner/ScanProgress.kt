package com.lochanpthakke.empee3.core.database.scanner

data class ScanProgress(
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentSongTitle: String? = null,
    val isComplete: Boolean = false,
)
