package com.cbulock.minescape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cbulock.minescape.data.*
import com.cbulock.minescape.game.*
import com.cbulock.minescape.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class ChallengeLayoutTest {
    @get:Rule val compose = createComposeRule()
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
    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi") fun archivePhone() {
        compose.setContent { MinescapeTheme(ThemeMode.LIGHT) {
            DailyArchive("2026-11-15", mapOf("2026-11-09" to DailyRecord("2026-11-09", DailyOutcome.WON)), {}, {})
        } }
        compose.onNodeWithTag("archive-2026-11-09").performClick()
        compose.onNodeWithText("Play as practice").assertIsDisplayed()
        capture("archive-phone")
    }
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi") fun archiveLargeText() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { MinescapeTheme(ThemeMode.DARK) { DailyArchive("2026-11-15", emptyMap(), {}, {}) } }
        compose.onNodeWithTag("archive-2026-11-09").performScrollTo().performClick()
        compose.onNodeWithText("Play as practice").performScrollTo().assertIsDisplayed()
        capture("archive-large-text")
    }
    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi") fun visualHintPhone() {
        val game = GameEngine(Random(3)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
        val hint = requireNotNull(LogicSolver.hint(game))
        compose.setContent { MinescapeTheme(ThemeMode.LIGHT) {
            Column(Modifier.fillMaxSize()) {
                Board(game, {}, {}, Modifier.weight(1f), hintClues = hint.clueIndices, hintCovered = hint.coveredIndices, movesEnabled = false)
                HintPanel(hint, false, {}, {}, {})
            }
        } }
        compose.onNodeWithText("Show answer").assertIsDisplayed()
        capture("visual-hint-phone")
    }
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi") fun visualHintLargeTextKeepsBoardAndActionsReachable() {
        RuntimeEnvironment.setFontScale(2f)
        val game = GameEngine(Random(3)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
        val hint = requireNotNull(LogicSolver.hint(game))
        compose.setContent { MinescapeTheme(ThemeMode.DARK) {
            Column(Modifier.fillMaxSize()) {
                Board(game, {}, {}, Modifier.weight(1f), hintIndex = hint.index, hintClues = hint.clueIndices,
                    hintCovered = hint.coveredIndices, movesEnabled = false)
                HintPanel(hint, true, {}, {}, {})
            }
        } }
        compose.onNodeWithTag("board").assertIsDisplayed()
        compose.onNodeWithText(if (hint.mine) "Flag square" else "Reveal square").performScrollTo().assertIsDisplayed()
        capture("visual-hint-large-text")
    }
}
