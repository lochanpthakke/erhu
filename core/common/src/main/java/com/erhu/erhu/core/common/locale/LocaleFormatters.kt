package com.erhu.erhu.core.common.locale

import android.content.Context
import com.erhu.erhu.core.common.R
import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

/** Locale-aware formatting utilities for numbers, dates, times, and file sizes. */
object LocaleFormatters {
    private const val PERCENT_SCALE = 100
    private const val BYTES_PER_KB = 1L shl 10
    private const val BYTES_PER_MB = 1L shl 20
    private const val BYTES_PER_GB = 1L shl 30
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600
    private const val MS_PER_SECOND = 1000L

    fun formatInteger(
        value: Number,
        locale: Locale = Locale.getDefault(),
    ): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun formatDecimal(
        value: Double,
        decimals: Int = 1,
        locale: Locale = Locale.getDefault(),
    ): String =
        NumberFormat
            .getNumberInstance(locale)
            .apply {
                minimumFractionDigits = decimals
                maximumFractionDigits = decimals
            }.format(value)

    fun formatPercent(
        fraction: Double,
        locale: Locale = Locale.getDefault(),
    ): String = NumberFormat.getPercentInstance(locale).format(fraction)

    fun formatPercentInt(
        percent: Int,
        locale: Locale = Locale.getDefault(),
    ): String = NumberFormat.getPercentInstance(locale).format(percent.toDouble() / PERCENT_SCALE)

    fun formatMediumDate(
        instant: Instant,
        locale: Locale = Locale.getDefault(),
    ): String =
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
            .format(instant)

    fun formatMediumDate(
        timestampMs: Long,
        locale: Locale = Locale.getDefault(),
    ): String = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(timestampMs))

    fun formatShortTime(
        timestampMs: Long,
        locale: Locale = Locale.getDefault(),
    ): String = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(timestampMs))

    fun formatMonthYear(
        yearMonth: YearMonth,
        locale: Locale = Locale.getDefault(),
    ): String = DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(yearMonth)

    fun formatMonthName(
        yearMonth: YearMonth,
        locale: Locale = Locale.getDefault(),
    ): String = DateTimeFormatter.ofPattern("LLLL", locale).format(yearMonth)

    fun formatFileSize(
        context: Context,
        bytes: Long,
        locale: Locale = AppLocaleManager.activeLocale(context),
    ): String =
        when {
            bytes >= BYTES_PER_GB ->
                context.getString(R.string.format_size_gb, formatDecimal(bytes / BYTES_PER_GB.toDouble(), decimals = 1, locale = locale))
            bytes >= BYTES_PER_MB ->
                context.getString(R.string.format_size_mb, formatDecimal(bytes / BYTES_PER_MB.toDouble(), decimals = 1, locale = locale))
            bytes >= BYTES_PER_KB ->
                context.getString(R.string.format_size_kb, formatDecimal(bytes / BYTES_PER_KB.toDouble(), decimals = 0, locale = locale))
            else -> context.getString(R.string.format_size_bytes, formatInteger(bytes, locale = locale))
        }

    fun formatDurationClock(
        durationMs: Long,
        locale: Locale = Locale.getDefault(),
    ): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L)) / MS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return if (hours > 0) {
            String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(locale, "%d:%02d", minutes, seconds)
        }
    }
}
