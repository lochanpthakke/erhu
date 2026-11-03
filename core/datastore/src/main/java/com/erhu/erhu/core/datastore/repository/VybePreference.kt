package com.erhu.erhu.core.datastore.repository

import com.erhu.erhu.core.datastore.proto.PlaybackStateProto

/** Older installations have no field; explicitly saved false must remain false. */
fun PlaybackStateProto.vybeEnabledOrDefault(): Boolean = if (hasVybeEnabled()) vybeEnabled else true
