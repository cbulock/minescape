package com.cbulock.minescape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.cbulock.minescape.data.*
import com.cbulock.minescape.game.*
import com.cbulock.minescape.ui.DailyShare
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/** Real app screens at Play's existing 1080 × 1920 phone asset size. */
@RunWith(RobolectricTestRunner::class)
class LocalizedScreenshotsTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun render(locale: String) {
        val engine = GameEngine(Random(42))
        val game = engine.reveal(Game(BoardConfig.preset(Difficulty.INTERMEDIATE)), 119).copy(elapsedMillis = 84000)
        val folder = File("build/outputs/localized-store-listings/$locale/phone-screenshots").apply { mkdirs() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun label(id: Int): String {
                var value = ""
                scenario.onActivity { value = it.getString(id) }
                return value
            }
            fun save(name: String) {
                compose.waitForIdle()
                scenario.onActivity { activity ->
                    val view = activity.window.decorView
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    view.draw(canvas)
                    val origin = IntArray(2).also(view::getLocationOnScreen)
                    // Composite native dialog windows over the actual app, rather than
                    // exporting transparent corners around a dialog-only capture.
                    WindowInspector.getGlobalWindowViews().filter { it !== view && it.isShown && it.width > 0 }.forEach { overlay ->
                        val attributes = overlay.layoutParams as? WindowManager.LayoutParams
                        if (attributes != null && attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                            canvas.drawColor(Color.argb((attributes.dimAmount * 255).toInt(), 0, 0, 0))
                        }
                        val location = IntArray(2).also(overlay::getLocationOnScreen)
                        canvas.save()
                        canvas.translate((location[0] - origin[0]).toFloat(), (location[1] - origin[1]).toFloat())
                        overlay.draw(canvas)
                        canvas.restore()
                    }
                    bitmap.setHasAlpha(false)
                    check(bitmap.width == 1080 && bitmap.height == 1920) { "Unexpected screenshot size: ${bitmap.width} × ${bitmap.height}" }
                    File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText(label(R.string.choose_your_board)).fetchSemanticsNodes().isNotEmpty() }
            save("04-choose-your-board")
            scenario.onActivity { activity -> runBlocking { GameRepository(activity).update {
                AppData(game = game, preferences = Preferences(theme = ThemeMode.LIGHT))
            } } }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("board").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(label(R.string.fit_board)).assertIsDisplayed()
            compose.onNodeWithContentDescription(label(R.string.flag_mode)).assertIsDisplayed()
            save("01-classic-gameplay")

            scenario.onActivity { activity -> runBlocking { GameRepository(activity).update {
                it.copy(preferences = it.preferences.copy(theme = ThemeMode.DARK))
            } } }
            compose.waitForIdle()
            save("02-dark-theme-gameplay")

            var won = game
            game.cells.indices.filter { !game.cells[it].mine }.forEach { won = engine.reveal(won, it) }
            scenario.onActivity { activity -> runBlocking { GameRepository(activity).update {
                it.copy(game = won, preferences = it.preferences.copy(theme = ThemeMode.LIGHT)).recordResult()
            } } }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("game-result").fetchSemanticsNodes().isNotEmpty() }
            save("03-winning-result")

            compose.onNode(hasText(label(R.string.new_game)) and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText(label(R.string.choose_your_board)).assertIsDisplayed()
            compose.onNodeWithText(label(R.string.learn_to_play)).performScrollTo().performClick()
            compose.onNodeWithTag("tutorial-board").assertIsDisplayed()
            save("05-learn-to-play")

            // QA preview, deliberately outside the phone screenshot upload directory.
            scenario.onActivity { activity ->
                val bitmap = DailyShare.card(DailyRecord("2026-10-09", DailyOutcome.WON, 87000), activity.resources)
                File(folder.parentFile, "daily-result-preview.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test @Config(qualifiers = "es-rES-w360dp-h640dp-xxhdpi") fun spanish() = render("es")
    @Test @Config(qualifiers = "pt-rBR-w360dp-h640dp-xxhdpi") fun brazilianPortuguese() = render("pt-BR")
    @Test @Config(qualifiers = "fr-rFR-w360dp-h640dp-xxhdpi") fun french() = render("fr")
}
