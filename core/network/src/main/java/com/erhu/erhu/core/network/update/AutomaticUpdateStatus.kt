package com.erhu.erhu.core.network.update

import android.content.Context
import com.erhu.erhu.core.common.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Structured record of the last automatic update check for locale-aware display. */
sealed interface AutomaticUpdateRecord {
    val checkedAtMillis: Long

    data class UpToDate(
        override val checkedAtMillis: Long,
    ) : AutomaticUpdateRecord

    data class Available(
        override val checkedAtMillis: Long,
        val tagName: String,
    ) : AutomaticUpdateRecord

    data class Error(
        override val checkedAtMillis: Long,
        val message: String,
    ) : AutomaticUpdateRecord
}

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
        private val _record = MutableStateFlow(readStoredRecord())
        val record = _record.asStateFlow()

        fun record(result: AppResult<UpdateCheck>) {
            val now = Instant.now()
            val millis = now.toEpochMilli()
            val newRecord =
                when (result) {
                    is AppResult.Error -> AutomaticUpdateRecord.Error(millis, result.error.message)
                    is AppResult.Success ->
                        when (val check = result.data) {
                            UpdateCheck.UpToDate -> AutomaticUpdateRecord.UpToDate(millis)
                            is UpdateCheck.Available -> AutomaticUpdateRecord.Available(millis, check.release.tagName)
                        }
                }
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
            val text = "Automatic check $now: $message"
            val editor =
                preferences
                    .edit()
                    .putLong("automatic_checked_at", millis)
                    .putString("automatic_summary", text)
            when (newRecord) {
                is AutomaticUpdateRecord.UpToDate ->
                    editor
                        .putString("automatic_outcome", "up_to_date")
                        .remove("automatic_tag")
                        .remove("automatic_error")
                is AutomaticUpdateRecord.Available ->
                    editor
                        .putString("automatic_outcome", "available")
                        .putString("automatic_tag", newRecord.tagName)
                        .remove("automatic_error")
                is AutomaticUpdateRecord.Error ->
                    editor
                        .putString("automatic_outcome", "error")
                        .putString("automatic_error", newRecord.message)
                        .remove("automatic_tag")
            }
            editor.apply()
            _summary.value = text
            _record.value = newRecord
        }

        private fun readStoredRecord(): AutomaticUpdateRecord? {
            val checkedAt = preferences.getLong("automatic_checked_at", -1L)
            if (checkedAt < 0L) return null
            return when (preferences.getString("automatic_outcome", null)) {
                "up_to_date" -> AutomaticUpdateRecord.UpToDate(checkedAt)
                "available" ->
                    preferences.getString("automatic_tag", null)?.takeIf(String::isNotBlank)?.let {
                        AutomaticUpdateRecord.Available(checkedAt, it)
                    }
                "error" ->
                    preferences.getString("automatic_error", null)?.let {
                        AutomaticUpdateRecord.Error(checkedAt, it)
                    }
                else -> null
            }
        }
    }
