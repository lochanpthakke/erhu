package com.erhu.erhu.core.common.locale

import android.content.Context
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.common.result.AppError

private const val HTTP_RATE_LIMITED = 429

/** Maps an [AppError] into a user-facing localized message without mutating [AppError.message]. */
fun AppError.localizedMessage(context: Context): String {
    val localized = AppLocaleManager.localizedContext(context)
    val locale = AppLocaleManager.activeLocale(localized)
    return when (this) {
        is AppError.WriteAccessRequired -> localized.getString(R.string.error_write_access_required)
        is AppError.TagWriteError -> tagWriteErrorText(localized, message)
        is AppError.NetworkError -> networkErrorText(localized, locale, code, message)
        is AppError.IoError -> ioErrorText(localized, message)
        is AppError.DatabaseError ->
            if (message.isNotBlank()) {
                localized.getString(R.string.error_database_detail, message)
            } else {
                localized.getString(R.string.error_database_generic)
            }
        is AppError.ParseError ->
            if (message.isNotBlank()) {
                localized.getString(R.string.error_parse_detail, message)
            } else {
                localized.getString(R.string.error_parse_generic)
            }
        is AppError.ValidationError -> message.ifBlank { localized.getString(R.string.error_validation_generic) }
        is AppError.Unknown -> localized.getString(R.string.error_unknown)
    }
}

private fun tagWriteErrorText(
    context: Context,
    rawMessage: String,
): String =
    when {
        rawMessage.contains("read-only", ignoreCase = true) ->
            context.getString(R.string.error_tag_write_read_only)
        rawMessage.contains("format can't be tagged", ignoreCase = true) ->
            context.getString(R.string.error_tag_write_unsupported_format)
        rawMessage.isNotBlank() ->
            context.getString(R.string.error_tag_write_failed_detail, rawMessage)
        else -> context.getString(R.string.error_tag_write_failed)
    }

private fun networkErrorText(
    context: Context,
    locale: java.util.Locale,
    code: Int?,
    rawMessage: String,
): String =
    when {
        code == HTTP_RATE_LIMITED ->
            context.getString(R.string.error_network_rate_limited)
        code != null ->
            context.getString(R.string.error_network_http, LocaleFormatters.formatInteger(code, locale))
        rawMessage.isNotBlank() ->
            context.getString(R.string.error_network_detail, rawMessage)
        else -> context.getString(R.string.error_network_generic)
    }

private fun ioErrorText(
    context: Context,
    rawMessage: String,
): String =
    when {
        rawMessage.contains("no longer exists", ignoreCase = true) ->
            context.getString(R.string.error_io_file_missing)
        rawMessage.isNotBlank() ->
            context.getString(R.string.error_io_detail, rawMessage)
        else -> context.getString(R.string.error_io_generic)
    }
