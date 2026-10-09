package com.camer.minesweeper

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
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
import java.io.File
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class FeatureFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var repository: GameRepository
    private lateinit var model: GameViewModel
    private fun saved() = runBlocking { repository.data.first() }
    private fun waitFor(predicate: () -> Boolean) = compose.waitUntil(15000) { compose.waitForIdle(); predicate() }
    private fun menu(label: String) {
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onAllNodesWithText(label).onLast().performClick()
    }
    private fun initialize(scenario: ActivityScenario<MainActivity>, value: AppData = AppData()) {
        scenario.onActivity { activity ->
            repository = GameRepository(activity); model = ViewModelProvider(activity)[GameViewModel::class.java]
            runBlocking { repository.update { value } }
        }
        waitFor { model.data.value?.let { it.game?.id == value.game?.id && it.dailyGame?.id == value.dailyGame?.id && it.preferences == value.preferences } == true }
    }
    @Test fun practiceControlsUndoLossAndHintsDoNotRecordScores() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            initialize(scenario)
            compose.onNodeWithContentDescription("Practice mode").performClick()
            waitFor { saved().preferences.practice }
            compose.onNodeWithText("Beginner").performScrollTo().performClick()
            waitFor { saved().game?.mode == PlayMode.PRACTICE }
            compose.onNodeWithTag("cell-40").performClick()
            waitFor { saved().game?.status != GameStatus.READY }
            if (saved().game!!.finished) {
                runBlocking { repository.update { it.copy(game = GameEngine(Random(3)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER), mode = PlayMode.PRACTICE), 40)) } }
                waitFor { saved().game?.status == GameStatus.PLAYING }
            }
            val before = saved().game!!
            model.cell(CellIntent(before.id, before.cells.indexOfFirst { it.mine }, CellAction.REVEAL, before.flagMode))
            waitFor { saved().game?.status == GameStatus.LOST }
            compose.onNodeWithText("Undo").performClick()
            waitFor { saved().game?.status == GameStatus.PLAYING }
            assertEquals(before.cells, saved().game!!.cells)
            assertEquals(1, saved().game!!.undosUsed)
            compose.onNodeWithText("Hint").performClick()
            waitFor { model.hintMessage.value != null }
            if (model.hint.value != null) {
                val hint = model.hint.value!!
                compose.onNodeWithText(if (hint.mine) "Flag square" else "Reveal square").performClick()
                waitFor { saved().game!!.cells[hint.index].let { if (hint.mine) it.flagged else it.revealed } }
            } else compose.onNodeWithText("Close").performClick()
            assertTrue(saved().statistics.isEmpty()); assertTrue(saved().noGuessStatistics.isEmpty()); assertTrue(saved().results.isEmpty())
            scenario.recreate()
            waitFor { model.data.value?.game?.mode == PlayMode.PRACTICE }
            assertTrue(saved().game!!.undosUsed >= 1)
        }
    }
    @Test fun dailySwitchResumeAndPracticeRetryPreserveRegularUntilConfirmed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val regular = GameEngine(Random(9)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
            initialize(scenario, AppData(game = regular))
            menu("Daily challenges")
            compose.onNodeWithText("Today's challenge · ${model.today()}").performClick()
            waitFor { saved().activeSlot == ActiveSlot.DAILY }
            val daily = saved().dailyGame!!
            assertEquals(regular.id, saved().game!!.id); assertEquals(regular.cells, saved().game!!.cells)
            menu("Resume regular game")
            waitFor { saved().activeSlot == ActiveSlot.REGULAR }
            assertEquals(daily.id, saved().dailyGame!!.id)
            menu("Resume daily challenge")
            waitFor { saved().activeSlot == ActiveSlot.DAILY }
            model.cell(CellIntent(daily.id, daily.cells.indexOfFirst { it.mine }, CellAction.REVEAL, daily.flagMode))
            waitFor { saved().dailyGame?.status == GameStatus.LOST }
            assertEquals(DailyOutcome.LOST, saved().dailyRecords[daily.dailyDate]?.outcome)
            compose.onNodeWithText("Practice again").performClick()
            compose.onNodeWithText("Keep playing").performClick()
            assertEquals(regular.id, saved().game!!.id)
            compose.onNodeWithText("Practice again").performClick()
            compose.onNodeWithText("Start new game").performClick()
            waitFor { saved().activeSlot == ActiveSlot.REGULAR && saved().game?.mode == PlayMode.PRACTICE }
            assertEquals(daily.dailyDate, saved().game!!.dailyDate)
            assertTrue(saved().statistics.isEmpty()); assertTrue(saved().results.isEmpty())
            assertEquals(DailyOutcome.LOST, saved().dailyRecords[daily.dailyDate]?.outcome)
        }
    }
    @Test fun quickReplayKeepsConfigurationAndRejectsOldGesture() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val engine = GameEngine(Random(9))
            var game = engine.reveal(Game(BoardConfig(8, 10, 12)), 44)
            game.cells.indices.filter { !game.cells[it].mine }.forEach { game = engine.reveal(game, it) }
            val finished = game
            initialize(scenario, AppData(game = finished, preferences = Preferences(lastCustom = finished.config)).recordResult())
            compose.onNodeWithText("Play again").performClick()
            waitFor { saved().game?.id != finished.id }
            val fresh = saved().game!!
            assertEquals(finished.config, fresh.config); assertEquals(GameStatus.READY, fresh.status)
            model.cell(CellIntent(finished.id, 44, CellAction.REVEAL, false))
            model.viewport(finished.id, Viewport(80f))
            menu("Statistics") // pauses and acts as a UI synchronization point
            waitFor { saved().game?.id == fresh.id }
            assertEquals(GameStatus.READY, saved().game!!.status)
            assertEquals(fresh.viewport, saved().game!!.viewport)
            assertEquals(1, saved().statistics[Difficulty.CUSTOM]?.wins)
            assertNull(saved().statistics[Difficulty.CUSTOM]?.bestMillis)
        }
    }
    @Test fun fileBackupPreviewCancelReplaceAndFailuresPreserveProgress() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val original = GameEngine(Random(8)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
            initialize(scenario, AppData(game = original, preferences = Preferences(ThemeMode.DARK)))
            menu("Settings")
            lateinit var file: File
            scenario.onActivity { file = File(it.cacheDir, "feature-test-backup.json") }
            model.exportBackup(Uri.fromFile(file))
            waitFor { model.busy.value == null && file.exists() && file.length() > 0 }
            val exported = file.readBytes()
            assertEquals(original.cells, Backups.preview(exported.inputStream()).data.game!!.cells)
            val replacement = Game(BoardConfig.preset(Difficulty.EXPERT))
            runBlocking { repository.update { AppData(game = replacement) } }
            waitFor { model.data.value?.game?.id == replacement.id }
            model.importBackup(Uri.fromFile(file)); waitFor { model.preview.value != null }
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(replacement.id, saved().game!!.id)
            model.importBackup(Uri.fromFile(file)); waitFor { model.preview.value != null }
            compose.onNodeWithText("Replace progress").performClick()
            waitFor { saved().game?.id == original.id }
            assertEquals(original.cells, saved().game!!.cells); assertEquals(ThemeMode.DARK, saved().preferences.theme)
            file.writeText("invalid")
            model.importBackup(Uri.fromFile(file)); waitFor { model.busy.value == null && model.error.value != null }
            assertEquals(original.id, saved().game!!.id); assertNull(model.preview.value)
            model.dismissError()
            model.exportBackup(Uri.fromFile(File(file.parentFile, "missing-parent/subdirectory/backup.json")))
            waitFor { model.busy.value == null && model.error.value != null }
            assertEquals(original.id, saved().game!!.id)
            file.delete()
        }
    }
    @Test fun noGuessUIUsesBackgroundGenerationAndKeepsScoresSeparate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            initialize(scenario)
            compose.onNodeWithContentDescription("No-guess presets").performClick()
            waitFor { saved().preferences.generation == GenerationMode.NO_GUESS }
            compose.onNodeWithText("Beginner").performScrollTo().performClick()
            waitFor { saved().game?.generation == GenerationMode.NO_GUESS }
            compose.onNodeWithTag("cell-40").performClick()
            waitFor { saved().game?.status != GameStatus.READY && model.busy.value == null }
            assertTrue(LogicSolver.solves(saved().game!!))
            val g = saved().game!!
            if (!g.finished) {
                model.cell(CellIntent(g.id, g.cells.indexOfFirst { it.mine }, CellAction.REVEAL, false))
                waitFor { saved().noGuessStatistics[Difficulty.BEGINNER]?.losses == 1 }
            } else assertEquals(1, saved().noGuessStatistics[Difficulty.BEGINNER]?.wins)
            assertTrue(saved().statistics.isEmpty())
        }
    }
    @Test fun replacingPreviousDayArchivesAttemptAndRetryRemainsPractice() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            initialize(scenario)
            model.startDaily("2026-10-09")
            waitFor { saved().dailyGame?.dailyDate == "2026-10-09" }
            val oldCells = saved().dailyGame!!.cells
            model.startDaily("2026-10-10")
            waitFor { saved().dailyGame?.dailyDate == "2026-10-10" }
            val currentId = saved().dailyGame!!.id
            assertEquals(DailyOutcome.ABANDONED, saved().dailyRecords["2026-10-09"]?.outcome)
            model.startDaily("2026-10-09")
            waitFor { saved().activeSlot == ActiveSlot.REGULAR }
            assertEquals(PlayMode.PRACTICE, saved().game!!.mode)
            assertEquals(oldCells, saved().game!!.cells)
            assertEquals(currentId, saved().dailyGame!!.id)
            assertEquals(DailyOutcome.ABANDONED, saved().dailyRecords["2026-10-09"]?.outcome)
            assertTrue(saved().statistics.isEmpty()); assertTrue(saved().results.isEmpty())
        }
    }

}
