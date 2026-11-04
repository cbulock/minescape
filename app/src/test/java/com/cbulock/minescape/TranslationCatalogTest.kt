package com.cbulock.minescape

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.cbulock.minescape.data.DailyOutcome
import com.cbulock.minescape.data.DailyRecord
import com.cbulock.minescape.game.*
import com.cbulock.minescape.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
class TranslationCatalogTest {
    private val locales = mapOf("es" to "values-es", "pt-BR" to "values-pt-rBR", "pt-PT" to "values-pt-rPT", "fr" to "values-fr", "de" to "values-de", "it" to "values-it")
    private fun document(file: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    private fun elements(file: File): List<Element> {
        val children = document(file).documentElement.childNodes
        return (0 until children.length).mapNotNull { children.item(it) as? Element }
    }
    private fun signature(value: String): List<String> = Regex("%[0-9]+\\\$[sdf]").findAll(value).map { it.value }.sorted().toList()

    @Test fun catalogsAreCompleteAndKeepEveryFormatArgument() {
        val source = elements(File("src/main/res/values/strings.xml")).filter { it.getAttribute("translatable") != "false" }
            .associateBy { it.tagName + ":" + it.getAttribute("name") }
        for ((tag, directory) in locales) {
            val file = File("src/main/res/$directory/strings.xml")
            assertTrue("Missing $tag catalog", file.isFile)
            val entries = elements(file)
            val target = entries.associateBy { it.tagName + ":" + it.getAttribute("name") }
            assertEquals("Duplicate resource in $tag", entries.size, target.size)
            assertEquals("Incomplete $tag", source.keys, target.keys)
            for ((key, english) in source) {
                val translated = target.getValue(key)
                if (english.tagName == "plurals") {
                    val englishItems = (0 until english.childNodes.length).mapNotNull { english.childNodes.item(it) as? Element }
                        .associate { it.getAttribute("quantity") to it.textContent }
                    val translatedItems = (0 until translated.childNodes.length).mapNotNull { translated.childNodes.item(it) as? Element }
                        .associate { it.getAttribute("quantity") to it.textContent }
                    assertTrue("$tag $key requires other", "other" in translatedItems)
                    assertTrue("$tag $key requires one", "one" in translatedItems)
                    for ((quantity, value) in translatedItems) {
                        assertEquals("$tag $key $quantity", signature(englishItems[quantity] ?: englishItems.getValue("other")), signature(value))
                        assertTrue("Blank $tag $key $quantity", value.isNotBlank())
                    }
                } else {
                    assertEquals("$tag $key", signature(english.textContent), signature(translated.textContent))
                    assertTrue("Blank $tag $key", translated.textContent.isNotBlank())
                }
            }
        }
    }

    @Test fun regionalLocalesResolveTranslatedScreensHintsAndShares() {
        val app = ApplicationProvider.getApplicationContext<MinescapeApplication>()
        for (tag in listOf("es-ES", "es-MX", "pt-BR", "pt-PT", "fr-FR", "fr-CA", "de-DE", "de-AT", "de-CH", "it-IT", "it-CH")) {
            val locale = Locale.forLanguageTag(tag)
            val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(locale) }).resources
            val strings = TextResources(resources)
            assertNotEquals(tag, "Choose your board", strings(R.string.choose_your_board))
            assertNotEquals(tag, "Preparing a no-guess board…", strings.message(UiMessage.Resource(R.string.preparing_no_guess)))
            assertEquals("Minescape", strings(R.string.app_name))
            val position = strings(R.string.row_column, strings(R.string.safe_square), "2", "3")
            assertTrue(position.contains("2") && position.contains("3"))
            assertFalse("English accessibility label in $tag", position.contains("row"))
            for (count in listOf(0, 1, 2, 1_000_000)) {
                for (id in listOf(R.plurals.hints_used, R.plurals.undos_used, R.plurals.mines_hit, R.plurals.adjacent_mines)) {
                    val value = strings.quantity(id, count, count)
                    assertFalse("Unformatted $tag $value", value.contains("%1\$"))
                }
                val hint = strings.hint(Hint(0, true, HintReason.BoardTotal(count), count))
                assertFalse("English hint in $tag", hint.contains("covered square"))
                assertFalse("Unformatted hint in $tag", hint.contains("%1\$"))
            }
            val share = DailyShare.text(DailyRecord("2026-10-09", DailyOutcome.WON, 87000), resources)
            assertTrue(share.startsWith("Minescape"))
            assertTrue(share.endsWith("https://play.google.com/store/apps/details?id=com.cbulock.minescape"))
            assertFalse("English share in $tag", share.contains("First attempt"))
            assertFalse(share.contains("%1\$"))
        }
    }

    @Test fun androidLanguageSettingsAdvertiseOnlyTheSupportedReleaseLanguages() {
        val file = File("src/main/res/xml/locales_config.xml")
        assertTrue("Missing system language metadata", file.isFile)
        val names = document(file).getElementsByTagName("locale")
        val tags = (0 until names.length).map { (names.item(it) as Element).getAttribute("android:name") }.toSet()
        assertEquals(setOf("en", "es", "pt-BR", "pt-PT", "fr", "de", "it"), tags)
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains("android:localeConfig=\"@xml/locales_config\""))
    }

    @Test fun preparedStoreDescriptionsFitPlayLimits() {
        for (tag in locales.keys) {
            val file = File("../store-listing/localized/$tag/store-listing.md")
            assertTrue("Missing $tag store listing", file.isFile)
            val markdown = file.readText()
            fun section(name: String) = markdown.substringAfter("## $name\n").substringBefore("\n## ").trim()
            val title = section("App name")
            val short = section("Short description").substringBefore("\n\n").trim()
            val full = section("Full description")
            assertEquals("Minescape", title)
            assertTrue("$tag short: ${short.length}", short.length in 1..80)
            assertTrue("$tag full: ${full.length}", full.length in 1..4000)
            assertFalse("Untranslated $tag listing", full.contains("Play your way"))
        }
    }
}
