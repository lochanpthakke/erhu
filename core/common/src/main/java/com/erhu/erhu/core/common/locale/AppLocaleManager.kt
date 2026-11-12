package com.erhu.erhu.core.common.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale

/** Metadata for one target application language. */
data class SupportedLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String,
    val androidResourceDir: String,
    val isRtl: Boolean = false,
)

/**
 * Coordinates per-app locale selection via AndroidX [AppCompatDelegate] and [LocaleManagerCompat],
 * persisting API < 33 fallback preferences for services/widgets without changing system locale.
 */
object AppLocaleManager {
    private const val PREFS_NAME = "erhu_app_locale"
    private const val KEY_SELECTED_TAG = "selected_language_tag"
    private const val LEGACY_INDONESIAN_TAG = "in"
    private const val CANONICAL_INDONESIAN_TAG = "id"

    /** Full catalog of the 20 target languages for erhu. */
    val allTargetLanguages: List<SupportedLanguage> =
        listOf(
            SupportedLanguage("en", "English", "English", "values"),
            SupportedLanguage("pl", "Polski", "Polish", "values-pl"),
            SupportedLanguage("es", "Espa\u00f1ol", "Spanish", "values-es"),
            SupportedLanguage("pt-BR", "Portugu\u00eas (Brasil)", "Portuguese (Brazil)", "values-pt-rBR"),
            SupportedLanguage("fr", "Fran\u00e7ais", "French", "values-fr"),
            SupportedLanguage("de", "Deutsch", "German", "values-de"),
            SupportedLanguage("id", "Bahasa Indonesia", "Indonesian", "values-in"),
            SupportedLanguage("ru", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439", "Russian", "values-ru"),
            SupportedLanguage("ar", "\u0627\u0644\u0639\u0631\u0628\u064a\u0629", "Arabic", "values-ar", isRtl = true),
            SupportedLanguage("ja", "\u65e5\u672c\u8a9e", "Japanese", "values-ja"),
            SupportedLanguage("ko", "\ud55c\uad6d\uc5b4", "Korean", "values-ko"),
            SupportedLanguage("zh-CN", "\u7b80\u4f53\u4e2d\u6587", "Simplified Chinese", "values-zh-rCN"),
            SupportedLanguage("tr", "T\u00fcrk\u00e7e", "Turkish", "values-tr"),
            SupportedLanguage("it", "Italiano", "Italian", "values-it"),
            SupportedLanguage("nl", "Nederlands", "Dutch", "values-nl"),
            SupportedLanguage("th", "\u0e44\u0e17\u0e22", "Thai", "values-th"),
            SupportedLanguage("vi", "Ti\u1ebfng Vi\u1ec7t", "Vietnamese", "values-vi"),
            SupportedLanguage("uk", "\u0423\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u0430", "Ukrainian", "values-uk"),
            SupportedLanguage("sv", "Svenska", "Swedish", "values-sv"),
            SupportedLanguage("ro", "Rom\u00e2n\u0103", "Romanian", "values-ro"),
        )

    /** Languages whose translations are complete and safe to expose in system & in-app selectors. */
    private val completedLanguageTags: Set<String> =
        setOf(
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
        )

    /** Languages currently exposed to users. Incomplete translations are never shown. */
    val supportedLanguages: List<SupportedLanguage>
        get() = allTargetLanguages.filter { it.tag in completedLanguageTags }

    private val _localeChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val localeChanges: SharedFlow<Unit> = _localeChanges.asSharedFlow()

    /** Returns the canonical supported language tag chosen for erhu, or null when following system default. */
    fun selectedLanguageTag(context: Context): String? {
        val fromAppCompat = AppCompatDelegate.getApplicationLocales()
        val fromPlatform =
            if (!fromAppCompat.isEmpty) {
                fromAppCompat
            } else {
                LocaleManagerCompat.getApplicationLocales(context)
            }
        val resolvedFromDelegate = resolveSupportedTag(fromPlatform)
        val persisted =
            if (resolvedFromDelegate == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                readLegacyFallbackTag(context)
            } else {
                null
            }
        return resolvedFromDelegate ?: persisted
    }

    /**
     * Applies an in-app language selection without touching device system settings.
     * Passing null restores "System default".
     */
    fun setLanguageTag(
        context: Context,
        languageTag: String?,
    ) {
        val canonical = languageTag?.let(::findSupportedTag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (canonical == null) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(canonical)
                }
        } else {
            writeLegacyFallbackTag(context, canonical)
        }
        val locales =
            if (canonical == null) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(canonical)
            }
        AppCompatDelegate.setApplicationLocales(locales)
        notifyLocaleChanged()
    }

    /** Restores persisted per-app locale on pre-Android 13 application startup. */
    fun restoreLegacyLocaleOnStartup(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && AppCompatDelegate.getApplicationLocales().isEmpty) {
            readLegacyFallbackTag(context)?.let { saved ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(saved))
            }
        }
    }

    /** Notifies listeners (e.g. home screen widgets) that locale configuration changed. */
    fun notifyLocaleChanged() {
        _localeChanges.tryEmit(Unit)
    }

    /** Returns the [SupportedLanguage] matching [tag], or null if unsupported. */
    fun findLanguage(tag: String?): SupportedLanguage? {
        val canonical = tag?.let(::findSupportedTag) ?: return null
        return supportedLanguages.firstOrNull { it.tag == canonical }
    }

    /** Returns the currently active [Locale] for erhu (selected app locale or system default). */
    fun activeLocale(context: Context): Locale {
        val selectedTag = selectedLanguageTag(context)
        return if (selectedTag != null) {
            Locale.forLanguageTag(selectedTag)
        } else {
            systemLocale(context)
        }
    }

    /** Returns a localized [Context] suitable for Services, Notifications, Workers, and Widgets. */
    fun localizedContext(context: Context): Context {
        val selectedTag = selectedLanguageTag(context) ?: return context
        val targetLocale = Locale.forLanguageTag(selectedTag)
        val currentLocale = ConfigurationCompat.getLocales(context.resources.configuration)[0]
        val matchesLocale =
            currentLocale != null &&
                normalizeLanguageCode(currentLocale.toLanguageTag()) == normalizeLanguageCode(targetLocale.toLanguageTag())
        return if (matchesLocale) {
            context
        } else {
            val config =
                Configuration(context.resources.configuration).apply {
                    setLocale(targetLocale)
                    setLayoutDirection(targetLocale)
                }
            context.createConfigurationContext(config)
        }
    }

    /** Human-readable name of the phone's current system language in its own script. */
    fun systemLanguageDisplayName(context: Context): String {
        val locale = systemLocale(context)
        val raw = locale.getDisplayName(locale).ifBlank { locale.displayLanguage }
        return raw.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
        }
    }

    /** Resolves system default locale from platform Resources or [context]. */
    fun systemLocale(context: Context): Locale {
        val systemLocales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        val contextLocales = ConfigurationCompat.getLocales(context.resources.configuration)
        return systemLocales[0] ?: contextLocales[0] ?: Locale.getDefault()
    }

    /** Resolves the best matching canonical tag from a [LocaleListCompat] limited to completed languages. */
    fun resolveSupportedTag(locales: LocaleListCompat): String? {
        var resolved: String? = null
        var index = 0
        while (index < locales.size() && resolved == null) {
            val locale = locales[index]
            if (locale != null) {
                resolved = findSupportedTag(locale.toLanguageTag()) ?: findSupportedTag(locale.language)
            }
            index++
        }
        return resolved
    }

    /** Canonicalizes a BCP-47 tag (including legacy ISO 639-1 `"in"` -> `"id"`) if supported. */
    fun findSupportedTag(rawTag: String): String? {
        val normalized = normalizeLanguageCode(rawTag)
        if (normalized.isEmpty()) return null
        val supported = supportedLanguages
        val exact = supported.firstOrNull { it.tag.equals(normalized, ignoreCase = true) }
        val base = normalized.substringBefore('-')
        val byLanguage = supported.firstOrNull { it.tag.equals(base, ignoreCase = true) }
        return (exact ?: byLanguage)?.tag
    }

    private fun normalizeLanguageCode(rawTag: String): String {
        val cleaned = rawTag.trim().replace('_', '-')
        val parts = cleaned.split('-')
        val first = parts.firstOrNull()?.lowercase(Locale.ROOT).orEmpty()
        val canonicalFirst = if (first == LEGACY_INDONESIAN_TAG) CANONICAL_INDONESIAN_TAG else first
        return if (parts.size <= 1) {
            canonicalFirst
        } else {
            canonicalFirst + "-" + parts.drop(1).joinToString("-")
        }
    }

    private fun readLegacyFallbackTag(context: Context): String? {
        val stored =
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SELECTED_TAG, null)
        return stored?.let(::findSupportedTag)
    }

    private fun writeLegacyFallbackTag(
        context: Context,
        canonicalTag: String?,
    ) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (canonicalTag == null) {
            editor.remove(KEY_SELECTED_TAG)
        } else {
            editor.putString(KEY_SELECTED_TAG, canonicalTag)
        }
        editor.apply()
    }
}
