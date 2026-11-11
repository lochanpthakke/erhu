package com.erhu.erhu.core.common.locale

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class LocaleFormattersTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun decimalAndPercent_respectLocaleConventions() {
        val polishDecimal = LocaleFormatters.formatDecimal(3.5, decimals = 1, locale = Locale.forLanguageTag("pl"))
        assertTrue(polishDecimal.contains(','))
        val englishDecimal = LocaleFormatters.formatDecimal(3.5, decimals = 1, locale = Locale.US)
        assertEquals("3.5", englishDecimal)
    }

    @Test
    fun monthYear_usesSpecifiedLocale() {
        val march = YearMonth.of(2026, 3)
        val polish = LocaleFormatters.formatMonthYear(march, Locale.forLanguageTag("pl"))
        assertTrue(polish.isNotBlank())
        assertTrue(polish.contains("2026"))
    }

    @Test
    fun fileSize_formatsReadableUnits() {
        val mb = LocaleFormatters.formatFileSize(context, 5L * 1024L * 1024L, Locale.US)
        assertEquals("5.0 MB", mb)
        val kb = LocaleFormatters.formatFileSize(context, 12L * 1024L, Locale.US)
        assertEquals("12 KB", kb)
    }

    @Test
    fun durationClock_formatsHoursAndMinutes() {
        assertEquals("3:05", LocaleFormatters.formatDurationClock(185_000L, Locale.US))
        assertEquals("1:01:01", LocaleFormatters.formatDurationClock(3_661_000L, Locale.US))
    }
}
