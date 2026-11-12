package com.erhu.erhu.core.common.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationResourceParityTest {
    private val placeholderRegex = Regex("""%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]|\{[a-zA-Z0-9_]+\}""")

    private val cldrRequiredQuantities: Map<String, Set<String>> =
        mapOf(
            "en" to setOf("one", "other"),
            "es" to setOf("one", "other"),
            "pt-BR" to setOf("one", "other"),
            "de" to setOf("one", "other"),
            "it" to setOf("one", "other"),
            "nl" to setOf("one", "other"),
            "sv" to setOf("one", "other"),
            "fr" to setOf("one", "many", "other"),
            "pl" to setOf("one", "few", "many", "other"),
            "ru" to setOf("one", "few", "many", "other"),
            "uk" to setOf("one", "few", "many", "other"),
            "ro" to setOf("one", "few", "other"),
            "ar" to setOf("zero", "one", "two", "few", "many", "other"),
            "id" to setOf("other"),
            "ja" to setOf("other"),
            "ko" to setOf("other"),
            "zh-CN" to setOf("other"),
            "th" to setOf("other"),
            "vi" to setOf("other"),
            "tr" to setOf("other"),
        )

    private fun tagToQualifier(tag: String): String =
        when (tag) {
            "pt-BR" -> "values-pt-rBR"
            "zh-CN" -> "values-zh-rCN"
            "id" -> "values-in"
            else -> "values-$tag"
        }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists() && File(dir, "core/common").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }

    private fun parseStrings(file: File): Pair<Map<String, String>, Map<String, Map<String, String>>> {
        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(file)
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Map<String, String>>()

        val root = doc.documentElement
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> strings[name] = node.textContent
                "plurals" -> {
                    val items = linkedMapOf<String, String>()
                    val itemNodes = node.getElementsByTagName("item")
                    for (j in 0 until itemNodes.length) {
                        val item = itemNodes.item(j) as Element
                        items[item.getAttribute("quantity")] = item.textContent
                    }
                    plurals[name] = items
                }
            }
        }
        return strings to plurals
    }

    private fun extractPlaceholders(text: String): List<String> =
        placeholderRegex.findAll(text).map { it.value }.sorted().toList()

    @Test
    fun allExposedLocales_haveFullStringPluralAndPlaceholderParity() {
        val root = repoRoot()
        val baseCommonFile = File(root, "core/common/src/main/res/values/strings.xml")
        val baseWidgetFile = File(root, "app/src/main/res/values/widget_strings.xml")
        val (baseStrings, basePlurals) = parseStrings(baseCommonFile)
        val (baseWidgetStrings, _) = parseStrings(baseWidgetFile)

        val exposedTags = AppLocaleManager.supportedLanguages.map { it.tag }
        assertTrue("Expected at least en in supportedLanguages", exposedTags.contains("en"))

        val localesConfigFile = File(root, "app/src/main/res/xml/locales_config.xml")
        val configDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(localesConfigFile)
        val configNodes = configDoc.getElementsByTagName("locale")
        val configTags =
            (0 until configNodes.length).map { idx ->
                (configNodes.item(idx) as Element).getAttribute("android:name")
            }
        assertEquals(
            "locales_config.xml must match AppLocaleManager.supportedLanguages",
            exposedTags.toSet(),
            configTags.toSet(),
        )

        for (tag in exposedTags) {
            if (tag == "en") continue
            val qualifier = tagToQualifier(tag)
            val commonFile = File(root, "core/common/src/main/res/$qualifier/strings.xml")
            val widgetFile = File(root, "app/src/main/res/$qualifier/widget_strings.xml")
            assertTrue("Missing $commonFile", commonFile.exists())
            assertTrue("Missing $widgetFile", widgetFile.exists())

            val (locStrings, locPlurals) = parseStrings(commonFile)
            assertEquals("[$tag] missing string keys", emptySet<String>(), baseStrings.keys - locStrings.keys)
            assertEquals("[$tag] extra string keys", emptySet<String>(), locStrings.keys - baseStrings.keys)

            for ((key, baseVal) in baseStrings) {
                val locVal = locStrings.getValue(key)
                assertEquals(
                    "[$tag] placeholder mismatch in string '$key'",
                    extractPlaceholders(baseVal),
                    extractPlaceholders(locVal),
                )
            }

            assertEquals("[$tag] missing plurals keys", emptySet<String>(), basePlurals.keys - locPlurals.keys)
            assertEquals("[$tag] extra plurals keys", emptySet<String>(), locPlurals.keys - basePlurals.keys)

            val requiredQuantities = cldrRequiredQuantities.getValue(tag)
            for ((key, baseItems) in basePlurals) {
                val locItems = locPlurals.getValue(key)
                assertTrue(
                    "[$tag] plurals '$key' missing CLDR quantities ${requiredQuantities - locItems.keys}",
                    locItems.keys.containsAll(requiredQuantities),
                )
                val baseOther = baseItems["other"] ?: baseItems.values.first()
                val expectedPlaceholders = extractPlaceholders(baseOther)
                for ((qty, text) in locItems) {
                    assertEquals(
                        "[$tag] placeholder mismatch in plurals '$key'[$qty]",
                        expectedPlaceholders,
                        extractPlaceholders(text),
                    )
                }
            }

            val (locWidgetStrings, _) = parseStrings(widgetFile)
            assertEquals("[$tag] missing widget string keys", emptySet<String>(), baseWidgetStrings.keys - locWidgetStrings.keys)
            assertEquals("[$tag] extra widget string keys", emptySet<String>(), locWidgetStrings.keys - baseWidgetStrings.keys)
            for ((key, baseVal) in baseWidgetStrings) {
                val locVal = locWidgetStrings.getValue(key)
                assertEquals(
                    "[$tag] placeholder mismatch in widget string '$key'",
                    extractPlaceholders(baseVal),
                    extractPlaceholders(locVal),
                )
            }
        }
    }
}
