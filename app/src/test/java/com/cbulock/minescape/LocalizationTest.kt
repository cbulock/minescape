package com.cbulock.minescape

import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.cbulock.minescape.game.*
import com.cbulock.minescape.ui.TextResources
import com.cbulock.minescape.ui.UiMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LocalizationTest {
    private fun resources(locale: Locale): Resources {
        val app = ApplicationProvider.getApplicationContext<MinescapeApplication>()
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(locale) }).resources
    }

    @Test fun quantitiesAndCoordinatesRemainReadable() {
        val strings = TextResources(resources(Locale.US))
        for (count in listOf(0, 1, 2)) {
            assertEquals("$count ${if (count == 1) "hint" else "hints"}", strings.quantity(R.plurals.hints_used, count, count))
            assertEquals("$count ${if (count == 1) "undo" else "undos"}", strings.quantity(R.plurals.undos_used, count, count))
            assertEquals("$count ${if (count == 1) "mine hit" else "mines hit"}", strings.quantity(R.plurals.mines_hit, count, count))
        }
        assertEquals("Row 3, column 7", strings(R.string.cell_position, 3, 7))
        assertEquals("1 adjacent mine", strings.quantity(R.plurals.adjacent_mines, 1, 1))
        assertEquals("Beginner", strings.difficulty(Difficulty.BEGINNER))
        assertEquals("0 wins · 0 losses · 0 daily attempts", strings.backupSummary(0, 0, 0))
        assertEquals("1 win · 1 loss · 1 daily attempt", strings.backupSummary(1, 1, 1))
        assertEquals("2 wins · 2 losses · 2 daily attempts", strings.backupSummary(2, 2, 2))
    }

    @Test fun pendingMessagesResolveAgainstTheCurrentConfiguration() {
        val message = UiMessage.Resource(R.string.preparing_no_guess)
        val english = TextResources(resources(Locale.US)).message(message)
        val expanded = TextResources(resources(Locale.forLanguageTag("en-XA"))).message(message)
        assertEquals("Preparing a no-guess board…", english)
        assertNotEquals(english, expanded)
        assertEquals(english, TextResources(resources(Locale.JAPAN)).message(message))
    }

    @Test fun germanAndItalianQuantitiesCoordinatesAndPendingMessages() {
        val message = UiMessage.Resource(R.string.preparing_no_guess)
        val german = TextResources(resources(Locale.GERMANY))
        val italian = TextResources(resources(Locale.ITALY))
        for (count in listOf(0, 1, 2)) {
            assertEquals("$count ${if (count == 1) "Tipp" else "Tipps"}", german.quantity(R.plurals.hints_used, count, count))
            assertEquals("$count ${if (count == 1) "Zug" else "Züge"} rückgängig", german.quantity(R.plurals.undos_used, count, count))
            assertEquals("$count ${if (count == 1) "suggerimento" else "suggerimenti"}", italian.quantity(R.plurals.hints_used, count, count))
            assertEquals("$count ${if (count == 1) "annullamento" else "annullamenti"}", italian.quantity(R.plurals.undos_used, count, count))
        }
        assertEquals("Zeile 3, Spalte 7", german(R.string.cell_position, 3, 7))
        assertEquals("Riga 3, colonna 7", italian(R.string.cell_position, 3, 7))
        assertNotEquals(german.message(message), italian.message(message))
        assertNotEquals("Preparing a no-guess board…", german.message(message))
        assertNotEquals("Preparing a no-guess board…", italian.message(message))
    }

    @Test fun hintsKeepStructuredEvidenceAndBoundedComparisonText() {
        val strings = TextResources(resources(Locale.US))
        val clue = HintReason.Clue(2, 3, 1)
        val safe = Hint(0, false, clue, 2)
        assertEquals("The clue at row 2, column 3 is 1. After accounting for the proven mines, these 2 covered squares contain no mines. This square is safe.", strings.hint(safe))
        assertTrue(strings.hint(safe.copy(coveredSquares = 1)).contains("this 1 covered square contains no mines"))
        assertTrue(strings.hint(safe.copy(mine = true, coveredSquares = 1)).endsWith("The remaining 1 covered square must be a mine. Flag this square."))
        var reason: HintReason = clue
        repeat(1500) { reason = HintReason.Comparison(reason, clue) }
        val rendered = strings.hint(safe.copy(reason = reason))
        assertEquals(600 + " After accounting for the proven mines, these 2 covered squares contain no mines. This square is safe.".length, rendered.length)
        assertTrue(rendered.startsWith("Comparing deductions: Comparing deductions:"))
        assertTrue(TextResources(resources(Locale.forLanguageTag("en-XA"))).hint(safe) != strings.hint(safe))
    }
}
