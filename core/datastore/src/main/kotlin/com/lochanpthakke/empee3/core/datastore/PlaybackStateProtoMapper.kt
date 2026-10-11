package com.lochanpthakke.empee3.core.datastore

import com.lochanpthakke.empee3.core.datastore.proto.PlaybackStateProto
import com.lochanpthakke.empee3.core.datastore.proto.RepeatModeProto
import com.lochanpthakke.empee3.core.model.PersistedPlaybackState
import com.lochanpthakke.empee3.core.model.RepeatMode

fun RepeatMode.toProto(): RepeatModeProto =
    when (this) {
        RepeatMode.OFF -> RepeatModeProto.REPEAT_MODE_OFF
        RepeatMode.ONE -> RepeatModeProto.REPEAT_MODE_ONE
        RepeatMode.ALL -> RepeatModeProto.REPEAT_MODE_ALL
    }

fun RepeatModeProto.toDomain(): RepeatMode =
    when (this) {
        RepeatModeProto.REPEAT_MODE_OFF -> RepeatMode.OFF
        RepeatModeProto.REPEAT_MODE_ONE -> RepeatMode.ONE
        RepeatModeProto.REPEAT_MODE_ALL -> RepeatMode.ALL
        RepeatModeProto.UNRECOGNIZED -> RepeatMode.OFF
    }

fun PersistedPlaybackState.toProto(): PlaybackStateProto = applyTo(PlaybackStateProto.getDefaultInstance())

/**
 * Writes the playback fields into [existing] while keeping every other field
 * (theme, layout, rollback flag…) that shares the same DataStore file.
 */
fun PersistedPlaybackState.applyTo(existing: PlaybackStateProto): PlaybackStateProto =
    existing
        .toBuilder()
        .clearQueueSongIds()
        .addAllQueueSongIds(queueSongIds)
        .setCurrentIndex(currentIndex)
        .setPositionMs(positionMs)
        .setShuffleModeEnabled(shuffleModeEnabled)
        .setRepeatMode(repeatMode.toProto())
        .clearQueueCollectionKeys()
        .addAllQueueCollectionKeys(queueCollectionKeys)
        .build()

fun PlaybackStateProto.toDomain(): PersistedPlaybackState =
    PersistedPlaybackState(
        queueSongIds = queueSongIdsList,
        currentIndex = currentIndex,
        positionMs = positionMs,
        shuffleModeEnabled = shuffleModeEnabled,
        repeatMode = repeatMode.toDomain(),
        queueCollectionKeys = queueCollectionKeysList,
    )
