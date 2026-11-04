package com.cbulock.minescape

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.cbulock.minescape.data.*
import com.cbulock.minescape.game.*
import com.cbulock.minescape.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LocalizationUiTest {
    @get:Rule val compose = createComposeRule()
    private var locale by mutableStateOf(Locale.forLanguageTag("en-XA"))

    private fun context(): Context {
        val app = ApplicationProvider.getApplicationContext<MinescapeApplication>()
        val desiredLocale = locale
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(desiredLocale) })
    }

    private fun label(id: Int) = context().getString(id)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            val context = context()
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration,
                LocalLayoutDirection provides if (locale.language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MinescapeTheme(ThemeMode.LIGHT, content = content)
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last { it.isShown && it.width > 0 }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/outputs/screenshots/localization-$name.png").apply { parentFile!!.mkdirs() }
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun expandedPhoneHomeKeepsActionsReachable() {
        home("expanded-phone-home")
    }

    private fun home(name: String) {
        assertNotEquals("Choose your board", label(R.string.choose_your_board))
        show { HomeScreen({}, null, {}, Preferences(), {}, {}, {}, {}) }
        capture(name)
        for (id in listOf(R.string.difficulty_beginner, R.string.difficulty_intermediate, R.string.difficulty_expert,
            R.string.make_a_custom_board, R.string.daily_challenge, R.string.learn_to_play, R.string.statistics)) {
            compose.onNodeWithText(label(id)).performScrollTo().assertIsDisplayed()
        }
    }

    private fun settings(name: String) {
        show { SettingsScreen(Preferences(), {}, {}, {}, {}) }
        capture(name)
        for (id in listOf(R.string.haptic_feedback, R.string.question_marks, R.string.tap_to_chord)) {
            compose.onNodeWithContentDescription(label(id)).performScrollTo().assertIsDisplayed()
        }
        for (id in listOf(R.string.export_progress, R.string.import_progress)) {
            compose.onNodeWithText(label(id)).performScrollTo().assertIsDisplayed()
        }
        capture("$name-backups")
        compose.onNodeWithText(label(R.string.done)).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun expandedLandscapeSettings() = settings("expanded-landscape-settings")

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun expandedLargeTextSettings() {
        RuntimeEnvironment.setFontScale(2f)
        settings("expanded-large-text-settings")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun canadianFrenchLargeTextSettings() {
        locale = Locale.CANADA_FRENCH
        RuntimeEnvironment.setFontScale(2f)
        settings("fr-ca-large-text-settings")
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label(R.string.settings)).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("The settings heading should have room for the complete French word", 1, layouts.single().lineCount)
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun brazilianPortugueseLandscapeSettings() {
        locale = Locale.forLanguageTag("pt-BR")
        settings("pt-br-landscape-settings")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun mexicanSpanishLargeTextHome() {
        locale = Locale.forLanguageTag("es-MX")
        RuntimeEnvironment.setFontScale(2f)
        home("es-mx-large-text-home")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun germanLargeTextHome() {
        locale = Locale.GERMANY
        RuntimeEnvironment.setFontScale(2f)
        home("de-large-text-home")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun italianLargeTextSettings() {
        locale = Locale.ITALY
        RuntimeEnvironment.setFontScale(2f)
        settings("it-large-text-settings")
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun germanLandscapeSettings() {
        locale = Locale.GERMANY
        settings("de-landscape-settings")
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun italianLandscapeHome() {
        locale = Locale.ITALY
        home("it-landscape-home")
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun rtlBoardTargetsStayAlignedWithTheCanvas() {
        locale = Locale.forLanguageTag("ar-XB")
        val game = Game(BoardConfig(5, 5, 1))
        val intents = mutableListOf<CellIntent>()
        show { Board(game, { intents.add(it) }, {}, Modifier.fillMaxSize()) }
        val first = compose.onNodeWithTag("cell-0").fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag("cell-1").fetchSemanticsNode().boundsInRoot
        assertTrue("Logical columns follow the canvas even in RTL", first.left < next.left)
        compose.onNodeWithTag("cell-0").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, intents.single().index) }
        capture("rtl-board")
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun configurationChangeRefreshesPendingTextAndOpenSharePreview() {
        locale = Locale.US
        val message = UiMessage.Resource(R.string.preparing_no_guess, cancellable = true)
        val record = DailyRecord("2026-10-09", DailyOutcome.WON, 87000)
        show {
            val strings = textResources()
            Text(strings.message(message))
            ShareResultButton(record)
        }
        compose.onNodeWithText("Preparing a no-guess board…").assertExists()
        compose.onNodeWithText("Share result").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithContentDescription(DailyShare.text(record, context().resources)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { locale = Locale.forLanguageTag("en-XA") }
        compose.waitUntil(10000) {
            compose.onAllNodesWithContentDescription(DailyShare.text(record, context().resources)).fetchSemanticsNodes().isNotEmpty()
        }
        capture("expanded-open-share-preview")
        for (tag in listOf("de-DE", "it-IT")) {
            compose.runOnIdle { locale = Locale.forLanguageTag(tag) }
            compose.waitUntil(10000) {
                compose.onAllNodesWithContentDescription(DailyShare.text(record, context().resources)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(label(R.string.preparing_no_guess)).assertExists()
            capture("$tag-open-share-preview")
        }
        compose.onNodeWithText(label(R.string.close)).performClick()
        compose.onNodeWithText(label(R.string.preparing_no_guess)).assertExists()
    }
}
