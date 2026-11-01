package com.erhu.erhu.core.database.builder

import android.net.Uri

data class ProposedMove(
    val sourceUri: Uri,
    val sourceParentUri: Uri? = null,
    val sourcePath: String,
    val destinationPath: String,
    val title: String,
    val artist: String,
    val album: String,
    val state: State,
) {
    enum class State {
        ALREADY_ORGANIZED,
        VALID,
        MISSING_METADATA,
        COLLISION,
    }
}
