package com.camer.minesweeper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.camer.minesweeper.data.*
import com.camer.minesweeper.game.*
import com.camer.minesweeper.ui.MinesweeperTheme
import com.camer.minesweeper.ui.StatisticsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StatisticsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val data = AppData(
        statistics = mapOf(
            Difficulty.BEGINNER to Statistics(18, 6, 42000, 3, 7),
            Difficulty.INTERMEDIATE to Statistics(8, 8, 126000, 0, 4),
            Difficulty.EXPERT to Statistics(2, 8, 305000, 1, 1),
            Difficulty.CUSTOM to Statistics(2, 2, currentStreak = 2, bestStreak = 2)),
        noGuessStatistics = mapOf(Difficulty.BEGINNER to Statistics(9, 1, 35000, 4, 6)),
        results = (1..24).map { index -> GameResult("result-$index", BoardConfig.preset(Difficulty.BEGINNER),
            GenerationMode.CLASSIC, index % 3 != 0, index * 1000L,
            "2026-10-${index.toString().padStart(2, '0')}T12:00:00Z", newBest = index == 23) })

    private fun show(dark: Boolean = false, empty: Boolean = false) {
        compose.setContent { MinesweeperTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            StatisticsScreen(if (empty) AppData() else data) {}
        } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last { it.isShown && it.width > 0 }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File("build/outputs/screenshots").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun overviewAndHistoryPhone() {
        show()
        compose.onNodeWithText("55.6%").assertIsDisplayed()
        capture("statistics-phone")
        compose.onNodeWithTag("statistics-list").performScrollToKey("history-title")
        compose.onNodeWithText("Recent results").assertIsDisplayed()
        capture("statistics-history")
        compose.onNodeWithTag("statistics-list").performScrollToKey("more")
        compose.onNodeWithText("Show more results (14 remaining)").performClick()
        compose.onNodeWithTag("statistics-list").performScrollToKey("result-result-5")
        compose.onNodeWithText("00:05").assertIsDisplayed()
        compose.onNodeWithTag("statistics-list").performScrollToKey("more")
        compose.onNodeWithText("Show more results (4 remaining)").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun modeAndDifficultyFiltersSurviveRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MinesweeperTheme(ThemeMode.LIGHT) { StatisticsScreen(data) {} } }
        compose.onNodeWithContentDescription("Difficulty filter: All difficulties").performClick()
        compose.onNodeWithText("Custom", useUnmergedTree = true).performClick()
        compose.onNodeWithText("50.0%").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Difficulty filter: Custom").assertIsDisplayed()
        compose.onNodeWithText("No-guess").performClick()
        compose.onNodeWithText("90.0%").assertIsDisplayed()
        compose.onNodeWithContentDescription("Difficulty filter: All difficulties").performClick()
        compose.onNodeWithText("Custom").assertDoesNotExist()
        compose.onNodeWithText("Expert", useUnmergedTree = true).performClick()
        compose.onNodeWithText("A fresh start").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun emptyDarkPhone() {
        show(dark = true, empty = true)
        compose.onNodeWithText("A fresh start").assertIsDisplayed()
        compose.onNodeWithText("0.0%").assertDoesNotExist()
        capture("statistics-empty-dark")
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun populatedDarkPhone() {
        show(dark = true)
        compose.onNodeWithText("55.6%").assertIsDisplayed()
        capture("statistics-dark")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun largeTextKeepsFiltersAndRecordsReachable() {
        RuntimeEnvironment.setFontScale(2f)
        show()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("No-guess").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            action(layouts)
            check(layouts.single().lineCount == 1) { "Mode label must not break within a word at 200% text" }
        }
        capture("statistics-large-text")
        compose.onNodeWithTag("statistics-list").performScrollToKey("difficulty-BEGINNER")
        compose.onNodeWithText("00:42").assertIsDisplayed()
        capture("statistics-large-text-records")
        compose.onNodeWithTag("statistics-list").performScrollToKey("rules")
        compose.onNodeWithText("What counts toward statistics?").performClick()
        compose.onNodeWithText("Hide counting details").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun landscapeScrollsAndKeepsCloseVisible() {
        show()
        compose.onNodeWithTag("statistics-list").performScrollToKey("difficulty-BEGINNER")
        compose.onNodeWithText("00:42").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        capture("statistics-landscape")
    }
}
