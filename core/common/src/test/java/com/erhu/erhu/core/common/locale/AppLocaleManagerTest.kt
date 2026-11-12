package com.erhu.erhu.core.common.locale

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.erhu.erhu.core.common.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P, Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class AppLocaleManagerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppLocaleManager.setLanguageTag(context, null)
    }

    @After
    fun tearDown() {
        AppLocaleManager.setLanguageTag(context, null)
    }

    @Test
    fun allTargetLanguages_containsExactTwentyExpectedTags() {
        val tags = AppLocaleManager.allTargetLanguages.map { it.tag }
        assertEquals(20, tags.size)
        assertEquals(
            listOf(
                "en",
                "pl",
                "es",
                "pt-BR",
                "fr",
                "de",
                "id",
                "ru",
                "ar",
                "ja",
                "ko",
                "zh-CN",
                "tr",
                "it",
                "nl",
                "th",
                "vi",
                "uk",
                "sv",
                "ro",
            ),
            tags,
        )
        assertTrue(AppLocaleManager.allTargetLanguages.first { it.tag == "ar" }.isRtl)
        assertEquals(1, AppLocaleManager.allTargetLanguages.count { it.isRtl })
    }

    @Test
    fun setLanguageTag_persistsAndRestoresSystemDefaultCleanly() {
        assertNull(AppLocaleManager.selectedLanguageTag(context))
        AppLocaleManager.setLanguageTag(context, "en")
        assertEquals("en", AppLocaleManager.selectedLanguageTag(context))
        AppLocaleManager.setLanguageTag(context, null)
        assertNull(AppLocaleManager.selectedLanguageTag(context))
    }

    @Test
    fun incompleteLanguage_isRejectedAndNotExposed() {
        val supportedTags = AppLocaleManager.supportedLanguages.map { it.tag }
        assertFalse(supportedTags.isEmpty())
        assertNull(AppLocaleManager.findSupportedTag("zz"))
    }

    @Test
    fun resolveSupportedTag_handlesRegionalFallbacks() {
        val locales = LocaleListCompat.forLanguageTags("en-GB")
        assertEquals("en", AppLocaleManager.resolveSupportedTag(locales))
    }

    @Test
    fun localizedContext_setsLtrDirectionForEnglish() {
        AppLocaleManager.setLanguageTag(context, "en")
        val localized = AppLocaleManager.localizedContext(context)
        assertEquals(View.LAYOUT_DIRECTION_LTR, localized.resources.configuration.layoutDirection)
    }

    @Test
    fun setLanguageTag_polishResolvesPolishResourcesAndResetsToSystemDefault() {
        AppLocaleManager.setLanguageTag(context, "pl")
        assertEquals("pl", AppLocaleManager.selectedLanguageTag(context))
        val localized = AppLocaleManager.localizedContext(context)
        assertEquals("Biblioteka", localized.getString(R.string.nav_library))
        assertEquals(View.LAYOUT_DIRECTION_LTR, localized.resources.configuration.layoutDirection)

        AppLocaleManager.setLanguageTag(context, null)
        assertNull(AppLocaleManager.selectedLanguageTag(context))

        val unknownFallback = AppLocaleManager.resolveSupportedTag(LocaleListCompat.forLanguageTags("zz-ZZ"))
        assertNull(unknownFallback)
        val unsupportedConfig = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("zz-ZZ")) }
        val unsupportedContext = context.createConfigurationContext(unsupportedConfig)
        assertEquals("Library", unsupportedContext.getString(R.string.nav_library))
    }

    @Test
    fun setLanguageTag_arabicSetsRtlLayoutDirectionAndLocalizedResources() {
        AppLocaleManager.setLanguageTag(context, "ar")
        assertEquals("ar", AppLocaleManager.selectedLanguageTag(context))
        val localized = AppLocaleManager.localizedContext(context)
        assertEquals(View.LAYOUT_DIRECTION_RTL, localized.resources.configuration.layoutDirection)
        assertTrue(localized.getString(R.string.nav_library).isNotBlank())
        assertTrue(localized.getString(R.string.nav_library) != "Library")
    }

    @Test
    fun setLanguageTag_handlesIndonesianLegacyTagAndRegionalLocales() {
        assertEquals("id", AppLocaleManager.findSupportedTag("in"))
        assertEquals("id", AppLocaleManager.findSupportedTag("in-ID"))
        assertEquals("pt-BR", AppLocaleManager.findSupportedTag("pt_BR"))
        assertEquals("zh-CN", AppLocaleManager.findSupportedTag("zh_CN"))

        for (tag in listOf("id", "pt-BR", "zh-CN", "ja", "ko", "th", "vi")) {
            AppLocaleManager.setLanguageTag(context, tag)
            assertEquals(tag, AppLocaleManager.selectedLanguageTag(context))
            val localized = AppLocaleManager.localizedContext(context)
            val label = localized.getString(R.string.nav_library)
            assertTrue("Expected non-blank localized label for $tag", label.isNotBlank())
            assertTrue("Expected non-English label for $tag", label != "Library")
        }
    }
}
