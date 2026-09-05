package com.lochanpthakke.empee3.core.datastore

import com.lochanpthakke.empee3.core.datastore.proto.RepeatModeProto
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
