package com.erhu.erhu.core.network.update

import android.content.Context
import com.erhu.erhu.core.common.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Automatic timing never gates a manual lookup. No credentials or APKs are persisted here. */
@Singleton
class AutomaticUpdateStatus
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
        private val _summary = MutableStateFlow(preferences.getString("automatic_summary", null))
        val summary = _summary.asStateFlow()

        fun record(result: AppResult<UpdateCheck>) {
            val message =
                when (result) {
                    is AppResult.Error -> result.error.message
                    is AppResult.Success ->
                        when (val check = result.data) {
                            UpdateCheck.UpToDate -> "You're on the latest version."
                            is UpdateCheck.Available ->
                                "Version ${check.release.tagName} is available. " +
                                    "Tap Check for Updates Now for details."
                        }
                }
            val now = Instant.now()
            val text = "Automatic check $now: $message"
            preferences
                .edit()
                .putLong("automatic_checked_at", now.toEpochMilli())
                .putString("automatic_summary", text)
                .apply()
            _summary.value = text
        }
    }
