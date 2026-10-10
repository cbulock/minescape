package com.camer.minesweeper

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.camer.minesweeper.data.*
import com.camer.minesweeper.game.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class LearningFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var repository: GameRepository
    private lateinit var model: GameViewModel
    private fun saved() = runBlocking { repository.data.first() }
    private fun waitFor(predicate: () -> Boolean) = compose.waitUntil(15000) { compose.waitForIdle(); predicate() }
    private fun initialize(scenario: ActivityScenario<MainActivity>, value: AppData) {
        scenario.onActivity {
            repository = GameRepository(it); model = ViewModelProvider(it)[GameViewModel::class.java]
            runBlocking { repository.update { value } }
        }
        waitFor { model.data.value?.game?.id == value.game?.id && model.data.value?.preferences == value.preferences }
    }
    private fun menu(label: String) {
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onAllNodesWithText(label).onLast().performClick()
    }
    @Test fun tutorialTeachesActualMovesAndPreservesGameAcrossRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val original = GameEngine(Random(6)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
            initialize(scenario, AppData(game = original, preferences = Preferences(tapToChord = false)))
            menu("How to play")
            compose.onNodeWithText("Learn with a guided board").performClick()
            compose.onNodeWithTag("tutorial-cell-24").performTouchInput { click() }
            compose.onNodeWithText("Step 1 of 3").assertIsDisplayed()
            compose.onNodeWithTag("tutorial-cell-6").performTouchInput { click() }
            compose.onNodeWithText("Step 2 of 3").assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithText("Step 2 of 3").assertIsDisplayed()
            compose.onNodeWithTag("tutorial-cell-0").performTouchInput { longClick() }
            compose.onNodeWithText("Step 3 of 3").assertIsDisplayed()
            compose.onNodeWithTag("tutorial-cell-6").performTouchInput { click() }
            compose.onNodeWithText("You're ready to play").assertIsDisplayed()
            compose.onNodeWithText("Done").performClick()
            assertEquals(original.id, saved().game!!.id); assertEquals(original.cells, saved().game!!.cells)
            assertFalse(saved().preferences.tapToChord); assertTrue(saved().statistics.isEmpty())
        }
    }
    @Test fun retrySameBoardUsesPracticeAndRejectsThePreviousBoardGesture() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val engine = GameEngine(Random(3))
            val opening = engine.reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
            val lost = engine.reveal(opening, opening.cells.indexOfFirst { it.mine }).copy(elapsedMillis = 62000)
            val old = AppData(game = lost).recordResult()
            initialize(scenario, old)
            compose.onNodeWithText("Retry same board in practice").performScrollTo().performClick()
            waitFor { saved().game?.id != lost.id }
            val retry = saved().game!!
            assertEquals(PlayMode.PRACTICE, retry.mode); assertEquals(opening.cells, retry.cells)
            model.cell(CellIntent(lost.id, lost.cells.indexOfFirst { it.mine }, CellAction.REVEAL, false))
            menu("Statistics")
            assertEquals(retry.cells, saved().game!!.cells)
            assertEquals(old.statistics, saved().statistics); assertEquals(old.results, saved().results)
            compose.onNodeWithText("Done").performClick()
            model.cell(CellIntent(retry.id, retry.cells.indexOfFirst { it.mine }, CellAction.REVEAL, false))
            waitFor { saved().game?.status == GameStatus.LOST }
            compose.onNodeWithText("Undo").performScrollTo().performClick()
            waitFor { saved().game?.status == GameStatus.PLAYING }
            assertEquals(retry.cells, saved().game!!.cells); assertEquals(old.results, saved().results)
        }
    }
    @Test fun settingsPersistAndQuestionMarksCycleInFlagMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            initialize(scenario, AppData(game = Game(BoardConfig.preset(Difficulty.BEGINNER))))
            menu("Settings")
            compose.onNodeWithContentDescription("Question marks").performScrollTo().performClick()
            waitFor { saved().preferences.questionMarks }
            compose.onNodeWithContentDescription("Tap to chord").performScrollTo().performClick()
            waitFor { !saved().preferences.tapToChord }
            compose.onNodeWithText("800 ms").performScrollTo().performClick()
            waitFor { saved().preferences.holdMillis == 800L }
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithContentDescription("Flag mode").performClick()
            waitFor { saved().game!!.flagMode }
            compose.onNodeWithTag("cell-40").performTouchInput { click() }
            waitFor { saved().game!!.cells[40].flagged }
            compose.onNodeWithTag("cell-40").performTouchInput { click() }
            waitFor { saved().game!!.cells[40].questioned }
            assertEquals(0, saved().game!!.flags); assertEquals(GameStatus.READY, saved().game!!.status)
            scenario.recreate()
            assertTrue(saved().game!!.cells[40].questioned); assertEquals(800L, saved().preferences.holdMillis)
            compose.onNodeWithTag("cell-40").performTouchInput { click() }
            waitFor { !saved().game!!.cells[40].questioned }
        }
    }
    @Test fun personalBestFeedbackIncludesPreviousBestAndRemainsAfterRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val engine = GameEngine(Random(3))
            var game = engine.reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
            game.cells.indices.filter { !game.cells[it].mine }.forEach { game = engine.reveal(game, it) }
            val data = AppData(game = game.copy(elapsedMillis = 10000), statistics = mapOf(Difficulty.BEGINNER to Statistics(wins = 1, bestMillis = 12000))).recordResult()
            initialize(scenario, data)
            compose.onNodeWithText("New personal best").assertIsDisplayed()
            compose.onNodeWithTag("game-result").assertIsDisplayed()
            compose.onNodeWithText("Previous best 00:12").assertIsDisplayed()
            compose.onNodeWithText("View board").performClick()
            menu("New game")
            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            waitFor { compose.onAllNodesWithText("View result").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("game-result").assertDoesNotExist()
            scenario.recreate()
            waitFor { compose.onAllNodesWithText("View result").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("game-result").assertDoesNotExist()
            compose.onNodeWithText("View result").performClick()
            compose.onNodeWithText("New personal best").assertIsDisplayed()
            assertEquals(2, saved().statistics[Difficulty.BEGINNER]?.wins)
        }
    }
}
