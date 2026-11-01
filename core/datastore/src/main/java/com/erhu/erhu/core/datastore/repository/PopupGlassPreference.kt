package com.erhu.erhu.core.datastore.repository

import com.erhu.erhu.core.datastore.proto.PlaybackStateProto

internal const val DEFAULT_POPUP_GLASS_OPACITY = 0.5f

internal fun Float.normalizedPopupGlassOpacity(): Float = if (isFinite()) coerceIn(0f, 1f) else DEFAULT_POPUP_GLASS_OPACITY

internal fun PlaybackStateProto.popupGlassOpacityOrDefault(): Float =
    if (hasPopupGlassOpacity()) popupGlassOpacity.normalizedPopupGlassOpacity() else DEFAULT_POPUP_GLASS_OPACITY
