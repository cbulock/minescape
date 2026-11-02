package com.cbulock.minescape

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cbulock.minescape.data.*
import com.cbulock.minescape.game.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class ChallengeFeaturesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var repository: GameRepository
    private lateinit var model: GameViewModel
    private fun saved() = runBlocking { repository.data.first() }
    private fun waitFor(predicate: () -> Boolean) = compose.waitUntil(15000) { compose.waitForIdle(); predicate() }
    private fun setup(scenario: ActivityScenario<MainActivity>, data: AppData) {
        scenario.onActivity {
            repository = GameRepository(it); model = ViewModelProvider(it)[GameViewModel::class.java]
            runBlocking { repository.update { data } }
        }
        waitFor { model.data.value?.game?.id == data.game?.id && saved().game?.id == data.game?.id }
    }
    private fun menu(label: String) {
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onAllNodesWithText(label).onLast().performClick()
    }
    @Test fun sharedBoardConfirmsSurvivesRecreationAndStartsPractice() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val original = GameEngine(Random(3)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
            val shared = GameEngine(Random(4)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
            setup(scenario, AppData(game = original))
            model.receiveChallenge(ChallengeLink.encode(shared))
            compose.onNodeWithText("Open shared board?").assertIsDisplayed()
            assertEquals(original.id, saved().game!!.id)
            scenario.recreate()
            compose.onNodeWithText("Open shared board?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(original.id, saved().game!!.id)
            model.receiveChallenge(ChallengeLink.encode(shared))
            compose.onNodeWithText("Open board").performClick()
            waitFor { saved().game?.id != original.id }
            val imported = saved().game!!
            assertEquals(PlayMode.PRACTICE, imported.mode)
            assertEquals(shared.cells, imported.cells)
            scenario.recreate()
            compose.onNodeWithText("Open shared board?").assertDoesNotExist()
            assertEquals(imported.id, saved().game!!.id)
        }
    }
    @Test fun archivePastDateStartsPracticeWithoutOfficialHistory() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            setup(scenario, AppData())
            menu("Daily challenges")
            compose.onNodeWithText("Browse archive").performClick()
            val past = LocalDate.now().minusDays(1)
            if (past.month != LocalDate.now().month) compose.onNodeWithContentDescription("Previous month").performClick()
            compose.onNodeWithTag("archive-$past").performScrollTo().performClick()
            compose.onNodeWithText("Play as practice").performScrollTo().performClick()
            waitFor { saved().game?.dailyDate == past.toString() }
            assertEquals(PlayMode.PRACTICE, saved().game!!.mode)
            assertTrue(saved().dailyRecords.isEmpty())
            assertNull(saved().dailyGame)
        }
    }
    @Test fun hintFirstShowsRegionThenAnswerAndAppliesOnlyOnRequest() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val game = GameEngine(Random(3)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER), mode = PlayMode.PRACTICE), 40)
            setup(scenario, AppData(game = game))
            model.requestHint()
            compose.onNodeWithText("Show answer").assertIsDisplayed()
            compose.onNodeWithText("Reveal square").assertDoesNotExist()
            compose.onNodeWithText("Flag square").assertDoesNotExist()
            waitFor { saved().game?.id == game.id }
            assertEquals(game.cells, saved().game!!.cells)
            compose.onNodeWithText("Show answer").performClick()
            val target = requireNotNull(model.hint.value)
            compose.onNodeWithText(if (target.mine) "Flag square" else "Reveal square").performClick()
            waitFor { saved().game?.id == game.id && saved().game?.cells != game.cells }
            assertEquals(1, saved().game!!.hintsUsed)
        }
    }
    @Test fun completedDailyLinkConfirmsRegularReplacementAndKeepsOfficialResult() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            setup(scenario, AppData())
            model.startDaily()
            waitFor { saved().dailyGame != null }
            val daily = saved().dailyGame!!
            val lost = GameEngine().reveal(daily, daily.cells.indexOfFirst { it.mine })
            val regular = GameEngine(Random(8)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
            val original = AppData(game = regular, dailyGame = lost, activeSlot = ActiveSlot.REGULAR).recordResult()
            setup(scenario, original)
            model.receiveChallenge(ChallengeLink.daily(model.today()))
            compose.onNodeWithText("Open shared board?").assertIsDisplayed()
            assertEquals(regular.id, saved().game!!.id)
            compose.onNodeWithText("Open board").performClick()
            waitFor { saved().game?.id != regular.id }
            assertEquals(PlayMode.PRACTICE, saved().game!!.mode)
            assertEquals(daily.dailyDate, saved().game!!.dailyDate)
            assertEquals(original.dailyRecords, saved().dailyRecords)
            assertEquals(lost.id, saved().dailyGame!!.id)
        }
    }
    @Test fun coldLaunchOpensExactBoardOnceAcrossRecreation() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        repository = GameRepository(context)
        runBlocking { repository.update { AppData() } }
        val board = GameEngine(Random(8)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(ChallengeLink.encode(board)))
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { model = ViewModelProvider(it)[GameViewModel::class.java] }
            waitFor { saved().game != null }
            val imported = saved().game!!
            assertEquals(board.cells, imported.cells)
            scenario.recreate()
            compose.waitForIdle()
            assertEquals(imported.id, saved().game!!.id)
            assertNull(model.pendingChallenge.value)
        }
    }
    @Test fun warmIntentClosesSettingsAndConfirmsBeforeReplacingGame() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val current = GameEngine(Random(4)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
            val shared = GameEngine(Random(5)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
            setup(scenario, AppData(game = current))
            menu("Settings")
            lateinit var launchIntent: Intent
            scenario.onActivity { activity ->
                launchIntent = Intent(activity.intent)
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity,
                    Intent(activity, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(ChallengeLink.encode(shared))))
            }
            compose.onNodeWithText("Open shared board?").assertIsDisplayed()
            assertEquals(current.id, saved().game!!.id)
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(current.id, saved().game!!.id)
            assertNull(model.pendingChallenge.value)
            // Rotation/consumed-intent behavior is covered by the cold-launch and pending-import tests.
            menu("Settings")
            compose.onNodeWithText("Open shared board?").assertDoesNotExist()
            // ActivityScenario matches lifecycle events by the original launch intent. Restore only
            // that test bookkeeping after verifying delivery; otherwise it ignores ON_DESTROY.
            scenario.onActivity { it.intent = launchIntent }
        }
    }
}
