package com.camer.minesweeper

import android.net.Uri
import android.view.KeyEvent
import android.view.inspector.WindowInspector
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.camer.minesweeper.data.*
import com.camer.minesweeper.game.*
import com.camer.minesweeper.ui.MinesweeperTheme
import com.camer.minesweeper.ui.ProgressDialog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog
import java.io.File
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class BackNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var model: GameViewModel
    private lateinit var repository: GameRepository

    private fun initialize(scenario: ActivityScenario<MainActivity>, value: AppData = AppData()) {
        scenario.onActivity {
            model = ViewModelProvider(it)[GameViewModel::class.java]
            repository = GameRepository(it)
            runBlocking { repository.update { value } }
        }
        compose.waitUntil(10000) {
            compose.waitForIdle()
            model.data.value?.let { it.game?.id == value.game?.id && it.dailyGame?.id == value.dailyGame?.id } == true
        }
    }

    private fun menu(label: String) {
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onAllNodesWithText(label).onLast().performClick()
    }

    private fun back(scenario: ActivityScenario<MainActivity>) {
        compose.waitForIdle()
        scenario.onActivity { activity ->
            val dialog = ShadowDialog.getLatestDialog()
            // Exercise the same dispatcher that receives Android's completed Back gesture.
            if (dialog?.isShowing == true) (dialog as ComponentDialog).onBackPressedDispatcher.onBackPressed()
            else activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
    }

    private fun assertRoot(scenario: ActivityScenario<MainActivity>) {
        val boardVisible = compose.onAllNodesWithTag("board").fetchSemanticsNodes().isNotEmpty()
        scenario.onActivity { assertEquals(boardVisible, it.onBackPressedDispatcher.hasEnabledCallbacks()) }
    }

    @Test fun pagesCloseWithBackEvenBeforeFirstGame() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            initialize(scenario)
            for (label in listOf("Statistics", "Settings", "How to play", "Daily challenges")) {
                menu(label)
                compose.onNodeWithText("Done").assertExists()
                back(scenario)
                compose.onNodeWithText("Done").assertDoesNotExist()
                compose.onNodeWithText("Beginner").assertExists()
                assertRoot(scenario)
            }
            compose.onNodeWithText("Learn to play").performScrollTo().performClick()
            compose.onNodeWithTag("tutorial-board").assertExists()
            back(scenario)
            compose.onNodeWithText("Beginner").assertExists()
            assertRoot(scenario)
        }
    }

    @Test fun pagesReturnToNewGameThenBackResumesUnchangedGame() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val game = Game(BoardConfig.preset(Difficulty.BEGINNER))
            initialize(scenario, AppData(game = game))
            assertRoot(scenario)
            menu("New game")
            for (label in listOf("Statistics", "Settings", "How to play", "Daily challenges")) {
                menu(label)
                back(scenario)
                compose.onNodeWithText("Beginner").assertExists()
                compose.onNodeWithText("Done").assertDoesNotExist()
                assertEquals(game.id, model.data.value?.game?.id)
            }
            back(scenario)
            compose.onNodeWithTag("board").assertIsDisplayed()
            assertEquals(game.cells, model.data.value?.game?.cells)
            assertRoot(scenario)
        }
    }

    @Test fun tutorialReturnsToHelpAndRestoresItsOriginAfterRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val game = Game(BoardConfig.preset(Difficulty.BEGINNER))
            initialize(scenario, AppData(game = game))
            menu("New game")
            menu("How to play")
            compose.onNodeWithText("Learn with a guided board").performClick()
            compose.onNodeWithTag("tutorial-board").assertExists()
            scenario.recreate()
            compose.waitForIdle()
            back(scenario)
            compose.onNodeWithText("Learn with a guided board").assertIsDisplayed()
            back(scenario)
            compose.onNodeWithText("Beginner").assertExists()
            back(scenario)
            compose.onNodeWithTag("board").assertIsDisplayed()
            assertRoot(scenario)
        }
    }

    @Test fun backCancelsReplacementWithoutReplacingOrLeavingPicker() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val game = Game(BoardConfig.preset(Difficulty.BEGINNER))
            initialize(scenario, AppData(game = game))
            menu("New game")
            compose.onNodeWithText("Intermediate").performScrollTo().performClick()
            compose.onNodeWithText("Replace current game?").assertExists()
            back(scenario)
            compose.onNodeWithText("Replace current game?").assertDoesNotExist()
            compose.onNodeWithText("Beginner").assertExists()
            assertEquals(game.id, model.data.value?.game?.id)
            assertTrue(model.data.value!!.results.isEmpty())
            back(scenario)
            compose.onNodeWithTag("board").assertIsDisplayed()
        }
    }

    @Test fun backReturnsFromResultToHomeWithoutStartingAnotherGame() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val engine = GameEngine(Random(8))
            val opened = engine.reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
            val lost = engine.reveal(opened, opened.cells.indexOfFirst { it.mine })
            initialize(scenario, AppData(game = lost).recordResult())
            compose.onNodeWithTag("game-result").assertIsDisplayed()
            back(scenario)
            compose.onNodeWithText("Retry same board in practice").assertDoesNotExist()
            compose.onNodeWithText("Choose your board").assertIsDisplayed()
            assertEquals(lost.id, model.data.value?.game?.id)
            assertEquals(GameStatus.LOST, model.data.value?.game?.status)
            assertRoot(scenario)
            scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithText("Choose your board").assertIsDisplayed()
            compose.onNodeWithText("View last game").performClick()
            compose.onNodeWithTag("board").assertIsDisplayed()
            compose.onNodeWithTag("game-result").assertDoesNotExist()
            back(scenario)
            compose.onNodeWithText("Choose your board").assertIsDisplayed()
        }
    }

    @Test fun backFromPlayingBoardReturnsHomeAndResumePreservesProgress() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val game = GameEngine(Random(8)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
            initialize(scenario, AppData(game = game))
            back(scenario)
            compose.onNodeWithText("Choose your board").assertIsDisplayed()
            assertRoot(scenario)
            menu("Settings")
            back(scenario)
            compose.onNodeWithText("Choose your board").assertIsDisplayed()
            compose.onNodeWithText("Back to current game").performClick()
            compose.onNodeWithTag("board").assertIsDisplayed()
            assertEquals(game.id, model.data.value?.game?.id)
            assertEquals(game.cells, model.data.value?.game?.cells)
        }
    }

    @Test fun finishedGameOffersNewGameInResultAndOnBoard() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (game in listOf(wonEffectFixture(), lostEffectFixture())) {
                initialize(scenario, AppData(game = game).recordResult())
                compose.onNode(hasText("New game") and hasAnyAncestor(isDialog())).assertIsDisplayed().performClick()
                compose.onNodeWithText("Choose your board").assertIsDisplayed()
                compose.onNodeWithText("View last game").performClick()
                compose.onNodeWithText("New game").assertIsDisplayed().performClick()
                compose.onNodeWithText("Intermediate").performScrollTo().performClick()
                compose.waitUntil(10000) { compose.waitForIdle(); model.data.value?.game?.id != game.id }
                compose.onNodeWithTag("board").assertIsDisplayed()
                assertEquals(Difficulty.INTERMEDIATE, model.data.value?.game?.config?.difficulty)
                assertFalse(model.data.value!!.game!!.finished)
            }
        }
    }

    @Test fun backCancelsImportPreviewAndReturnsToSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val game = Game(BoardConfig.preset(Difficulty.BEGINNER))
            initialize(scenario, AppData(game = game))
            menu("New game")
            menu("Settings")
            lateinit var file: File
            scenario.onActivity { activity ->
                file = File(activity.cacheDir, "back-preview.json")
                file.writeBytes(Backups.export(AppData(game = Game(BoardConfig.preset(Difficulty.EXPERT)))))
                model.importBackup(Uri.fromFile(file))
            }
            compose.waitUntil(10000) { compose.waitForIdle(); model.preview.value != null }
            back(scenario)
            compose.onNodeWithText("Replace progress with backup?").assertDoesNotExist()
            compose.onNodeWithText("Settings").assertExists()
            assertNull(model.preview.value)
            assertEquals(game.id, model.data.value?.game?.id)
            back(scenario)
            compose.onNodeWithText("Beginner").assertExists()
            file.delete()
        }
    }

    @Test fun menuBackClosesOnlyMenuOnNewGameScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            initialize(scenario, AppData(game = Game(BoardConfig.preset(Difficulty.BEGINNER))))
            menu("New game")
            compose.onNodeWithContentDescription("Open menu").performClick()
            compose.runOnIdle {
                val popup = WindowInspector.getGlobalWindowViews().single { it.javaClass.simpleName == "PopupLayout" }
                popup.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                popup.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            }
            compose.onNodeWithText("Settings").assertDoesNotExist()
            compose.onNodeWithText("Beginner").assertExists()
            back(scenario)
            compose.onNodeWithTag("board").assertIsDisplayed()
        }
    }

    @Test fun backDismissesHintWithoutApplyingIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val game = Tutorial.game(0).copy(mode = PlayMode.PRACTICE)
            initialize(scenario, AppData(game = game))
            model.requestHint()
            compose.waitUntil(10000) { compose.waitForIdle(); model.hintMessage.value != null }
            back(scenario)
            assertNull(model.hintMessage.value)
            assertNull(model.hint.value)
            assertEquals(game.cells, model.data.value?.game?.cells)
            assertRoot(scenario)
        }
    }

    @Test fun backDismissesRecoveryBeforeLeavingHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            initialize(scenario, AppData(recovered = true))
            compose.onNodeWithText("Saved data reset").assertExists()
            back(scenario)
            compose.waitUntil(10000) { compose.waitForIdle(); model.data.value?.recovered == false }
            compose.onNodeWithText("Saved data reset").assertDoesNotExist()
            compose.onNodeWithText("Beginner").assertExists()
            assertRoot(scenario)
        }
    }

    @Test fun backPreservesDailySlotAndMenuResumeClearsNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val regular = Game(BoardConfig.preset(Difficulty.BEGINNER))
            initialize(scenario, AppData(game = regular))
            model.startDaily("2026-10-10")
            compose.waitUntil(10000) { compose.waitForIdle(); model.data.value?.activeSlot == ActiveSlot.DAILY }
            val daily = model.data.value!!.dailyGame!!
            menu("New game")
            menu("Daily challenges")
            back(scenario)
            compose.onNodeWithText("Beginner").assertExists()
            back(scenario)
            compose.onNodeWithTag("board").assertIsDisplayed()
            assertEquals(daily.id, model.data.value?.activeGame?.id)
            assertEquals(regular.id, model.data.value?.game?.id)
            menu("New game")
            menu("Resume regular game")
            compose.waitUntil(10000) { compose.waitForIdle(); model.data.value?.activeSlot == ActiveSlot.REGULAR }
            compose.onNodeWithTag("board").assertIsDisplayed()
            assertRoot(scenario)
            assertEquals(daily.id, model.data.value?.dailyGame?.id)
        }
    }

    @Test fun backClosesGameOptionsBeforeLeavingNewGame() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            initialize(scenario, AppData(game = Game(BoardConfig.preset(Difficulty.BEGINNER))))
            menu("New game")
            compose.onNodeWithContentDescription("Game options").performClick()
            compose.onNodeWithContentDescription("Practice mode").assertExists()
            back(scenario)
            compose.onNodeWithContentDescription("Practice mode").assertDoesNotExist()
            compose.onNodeWithText("Beginner").assertExists()
            back(scenario)
            compose.onNodeWithTag("board").assertIsDisplayed()
        }
    }

    @Test fun backDismissesErrorBeforeLeavingPicker() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val game = Game(BoardConfig.preset(Difficulty.BEGINNER))
            initialize(scenario, AppData(game = game))
            menu("New game")
            lateinit var file: File
            scenario.onActivity {
                file = File(it.cacheDir, "invalid-back-backup.json")
                file.writeText("invalid")
                model.importBackup(Uri.fromFile(file))
            }
            compose.waitUntil(10000) { compose.waitForIdle(); model.error.value != null && model.busy.value == null }
            back(scenario)
            assertNull(model.error.value)
            compose.onNodeWithText("Beginner").assertExists()
            assertEquals(game.id, model.data.value?.game?.id)
            back(scenario)
            compose.onNodeWithTag("board").assertIsDisplayed()
            file.delete()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
class ProgressBackTest {
    @get:Rule val compose = createComposeRule()

    private fun checkBack(message: String, cancellable: Boolean) {
        var cancellations = 0
        compose.setContent { MinesweeperTheme(ThemeMode.LIGHT) { ProgressDialog(message) { cancellations++ } } }
        compose.waitForIdle()
        compose.runOnIdle { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        assertEquals(if (cancellable) 1 else 0, cancellations)
        if (!cancellable) compose.onNodeWithText(message).assertIsDisplayed()
    }

    @Test fun backCancelsBoardGeneration() = checkBack("Preparing a no-guess board…", true)
    @Test fun backCancelsHintCalculation() = checkBack("Finding a deduction…", true)
    @Test fun backDoesNotInterruptBackupWrite() = checkBack("Exporting backup…", false)
    @Test fun backDoesNotInterruptBackupRead() = checkBack("Checking backup…", false)
}
