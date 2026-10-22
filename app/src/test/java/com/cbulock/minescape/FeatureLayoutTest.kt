package com.cbulock.minescape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.cbulock.minescape.data.*
import com.cbulock.minescape.game.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class FeatureLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun render(name: String, screen: String? = null, practice: Boolean = false, daily: Boolean = false,
                       lost: Boolean = false, dark: Boolean = false, largeText: Boolean = false, won: Boolean = false) {
        if (largeText) RuntimeEnvironment.setFontScale(2f)
        val engine = GameEngine(Random(3))
        val challenge = DailyChallenge(Json.decodeFromString(File("src/main/assets/daily-v1.json").readText()))
        var game = if (daily) challenge.game("2026-10-09") else engine.reveal(Game(BoardConfig.preset(Difficulty.INTERMEDIATE),
            mode = if (practice) PlayMode.PRACTICE else PlayMode.NORMAL), 119)
        game = game.copy(elapsedMillis = 84000)
        if (lost) game = engine.act(game, game.cells.indexOfFirst { it.mine }, CellAction.REVEAL)
        if (won) game.cells.indices.filter { !game.cells[it].mine }.forEach { game = engine.reveal(game, it) }
        val data = AppData(game = if (daily) null else game, dailyGame = if (daily) game else null,
            activeSlot = if (daily) ActiveSlot.DAILY else ActiveSlot.REGULAR,
            preferences = Preferences(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, questionMarks = true, holdMillis = 800, tapToChord = false),
            statistics = if (won) mapOf(Difficulty.INTERMEDIATE to Statistics(wins = 1, bestMillis = 105000)) else emptyMap(),
            dailyRecords = mapOf("2026-10-08" to DailyRecord("2026-10-08", DailyOutcome.WON, 67000)) +
                if (daily) mapOf("2026-10-09" to DailyRecord("2026-10-09", DailyOutcome.IN_PROGRESS)) else emptyMap()).recordResult()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: GameViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[GameViewModel::class.java]
                runBlocking { GameRepository(it).update { data } }
            }
            compose.waitUntil(10000) { compose.waitForIdle(); compose.onAllNodesWithTag("board").fetchSemanticsNodes().isNotEmpty() }
            when (screen) {
                "tutorial" -> {
                    compose.onNodeWithContentDescription("Open menu").performClick()
                    compose.onNodeWithText("How to play").performClick()
                    compose.onNodeWithText("Learn with a guided board").performClick()
                    compose.onNodeWithTag("tutorial-board").assertIsDisplayed()
                    compose.onNodeWithText("Step 1 of 3").assertIsDisplayed()
                    val board = compose.onNodeWithTag("tutorial-board").fetchSemanticsNode().boundsInRoot
                    check(board.height > 80f) { "Tutorial board collapsed: $board" }
                    val cell = compose.onNodeWithTag("tutorial-cell-6").fetchSemanticsNode().boundsInRoot
                    check(cell.width >= 64f) { "Lesson cells should be at least 32 dp on these xhdpi screens: $cell" }
                }
                "result" -> {
                    compose.onNodeWithTag("game-result").assertIsDisplayed()
                    compose.onNode(hasText("New game") and hasAnyAncestor(isDialog())).assertIsDisplayed()
                    if (daily) compose.onNode(hasText("Share daily result") and hasAnyAncestor(isDialog())).assertIsDisplayed()
                    compose.onNodeWithText("Result details").assertDoesNotExist()
                    if (won && !daily) compose.onNodeWithText("Previous best 01:45").performScrollTo().assertIsDisplayed()
                    val action = if (daily) "Practice again" else "Retry same board in practice"
                    val button = compose.onNode(hasText(action) and hasAnyAncestor(isDialog()))
                    button.performScrollTo().assertIsDisplayed()
                    val bounds = button.fetchSemanticsNode().boundsInRoot
                    val root = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
                    check(bounds.left >= root.left && bounds.right <= root.right) { "Result action exceeds dialog width" }
                    compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog()))
                        .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10000f) }
                    compose.onNode(hasText(if (won) "You won" else "Game over") and hasAnyAncestor(isDialog())).assertIsDisplayed()
                }
                "controls" -> {
                    compose.onNodeWithContentDescription("Open menu").performClick()
                    compose.onNodeWithText("Settings").performClick()
                    compose.onNodeWithText("800 ms").performScrollTo().assertIsDisplayed()
                }
                "backup" -> {
                    scenario.onActivity {
                        val file = File(it.cacheDir, "preview-test.json"); file.writeBytes(Backups.export(data))
                        model.importBackup(Uri.fromFile(file))
                    }
                    compose.waitUntil(10000) { compose.waitForIdle(); model.preview.value != null }
                    compose.onNodeWithText("Replace progress").assertIsDisplayed()
                }
                null -> {
                    if (game.finished) compose.onNodeWithText("View board").performClick()
                    compose.onNodeWithTag("board").assertIsDisplayed()
                    compose.onNodeWithText("Fit board").assertIsDisplayed()
                    if (game.finished) {
                        compose.onNodeWithText("New game").assertIsDisplayed()
                        if (daily) compose.onNodeWithText("Share daily result").assertIsDisplayed()
                        val board = compose.onNodeWithTag("board").fetchSemanticsNode().boundsInRoot
                        check(board.height >= 160f) { "Finished actions leave too little board space: $board" }
                    }
                    if (practice && !lost) {
                        compose.onNodeWithText("Undo").assertIsDisplayed()
                        if (!lost) compose.onNodeWithText("Hint").assertIsDisplayed()
                    }
                    val board = compose.onNodeWithTag("board").fetchSemanticsNode().boundsInRoot
                    val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                    check(board.height > root.height * .4f) { "Board became too small: $board in $root" }
                }
                else -> {
                    compose.onNodeWithContentDescription("Open menu").performClick()
                    compose.onAllNodesWithText(screen).onLast().performClick()
                    compose.onNodeWithText("Done").assertIsDisplayed()
                }
            }
            compose.waitForIdle()
            scenario.onActivity { activity ->
                // Include the actual dialog window, rather than capturing the activity behind it.
                val view = WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it.width > 0 } ?: activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val folder = File("build/outputs/screenshots").apply { mkdirs() }
                File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun practicePhone() = render("feature-practice", practice = true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi") fun practiceLossLandscape() = render("feature-practice-loss-landscape", practice = true, lost = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun practiceLargeText() = render("feature-practice-large-text", practice = true, largeText = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun dailyPhone() = render("feature-daily", daily = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun statsDarkPhone() = render("feature-stats-dark", screen = "Statistics", dark = true)
    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi") fun historyTablet() = render("feature-daily-history-tablet", screen = "Daily challenges", daily = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun backupLargeText() = render("feature-backup-large-text", screen = "backup", largeText = true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi") fun settingsLandscape() = render("feature-settings-landscape", screen = "Settings")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun tutorialPhone() = render("learning-tutorial-phone", screen = "tutorial")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi") fun tutorialLandscape() = render("learning-tutorial-landscape", screen = "tutorial")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun tutorialLargeText() = render("learning-tutorial-large-text", screen = "tutorial", largeText = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun recordDetails() = render("learning-personal-best", screen = "result", won = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun lossDetails() = render("result-loss-details", screen = "result", lost = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun practiceDetailsDark() = render("result-practice-details-dark", screen = "result", lost = true, practice = true, dark = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun resultDetailsLargeText() = render("result-details-large-text", screen = "result", won = true, largeText = true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi") fun resultDetailsLandscape() = render("result-details-landscape", screen = "result", won = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun dailyResultDetails() = render("result-daily-details", screen = "result", lost = true, daily = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun dailyResultLargeText() = render("result-daily-large-text", screen = "result", won = true, daily = true, largeText = true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi") fun dailyResultLandscape() = render("result-daily-landscape", screen = "result", lost = true, daily = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun dailyFinishedBoard() = render("daily-finished-board", won = true, daily = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun dailyFinishedBoardLargeText() = render("daily-finished-board-large-text", lost = true, daily = true, largeText = true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi") fun dailyFinishedBoardLandscape() = render("daily-finished-board-landscape", lost = true, daily = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun controlSettings() = render("learning-controls", screen = "controls")
}
