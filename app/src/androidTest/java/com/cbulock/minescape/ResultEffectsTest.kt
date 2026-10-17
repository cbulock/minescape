package com.cbulock.minescape

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cbulock.minescape.data.ThemeMode
import com.cbulock.minescape.game.*
import com.cbulock.minescape.ui.Board
import com.cbulock.minescape.ui.MinescapeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal fun wonEffectFixture(): Game {
    var game = Tutorial.game(0)
    game.cells.indices.filter { !game.cells[it].mine }.forEach { game = GameEngine().reveal(game, it) }
    check(game.status == GameStatus.WON)
    return game
}
internal fun lostEffectFixture() = GameEngine().act(Tutorial.game(0), 0, CellAction.REVEAL)

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ResultEffectsTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })
    private var game by mutableStateOf(Tutorial.game(0))
    private var enabled by mutableStateOf(true)
    private var viewportChanges = 0
    private fun launch(initial: Game = Tutorial.game(0)) {
        game = initial
        compose.setContent { MinescapeTheme(ThemeMode.LIGHT) {
            Board(game, {}, { viewportChanges++ }, Modifier.fillMaxSize(), inputEnabled = enabled)
        } }
        compose.mainClock.autoAdvance = false
    }
    private fun transition(next: Game) {
        // With automatic frames off, publish the state change before advancing the test clock.
        compose.runOnIdle { game = next; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
    }
    @Test fun freshWinCelebratesOnceAndControlsStillWork() {
        launch(); transition(wonEffectFixture())
        compose.onNodeWithTag("result-confetti").assertExists()
        compose.onNodeWithTag("result-explosion").assertDoesNotExist()
        compose.onNodeWithContentDescription("Zoom in").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(1, viewportChanges) }
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithTag("result-confetti").assertDoesNotExist()
        transition(game.copy(elapsedMillis = 9999))
        compose.onNodeWithTag("result-confetti").assertDoesNotExist()
    }
    @Test fun mineHitExplodesAndUndoAllowsAnotherBurst() {
        launch(); transition(lostEffectFixture())
        compose.onNodeWithTag("result-explosion").assertExists()
        compose.onNodeWithTag("result-confetti").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithTag("result-explosion").assertDoesNotExist()
        transition(GameEngine().undo(game))
        compose.runOnIdle { assertEquals(GameStatus.PLAYING, game.status) }
        transition(GameEngine().act(game, 0, CellAction.REVEAL))
        compose.onNodeWithTag("result-explosion").assertExists()
    }
    @Test fun completedGamesAndNewIdentitiesDoNotReplay() {
        launch(wonEffectFixture())
        compose.onNodeWithTag("result-confetti").assertDoesNotExist()
        transition(lostEffectFixture().copy(id = "restored-loss"))
        compose.onNodeWithTag("result-explosion").assertDoesNotExist()
    }
    @Test fun menuOrBackgroundCancelsWithoutReplay() {
        launch(); transition(wonEffectFixture())
        compose.onNodeWithTag("result-confetti").assertExists()
        compose.runOnIdle { enabled = false; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("result-confetti").assertDoesNotExist()
        compose.runOnIdle { enabled = true; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("result-confetti").assertDoesNotExist()
    }
    @Test fun recreationDuringCelebrationDoesNotReplay() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MinescapeTheme(ThemeMode.LIGHT) {
            Board(game, {}, {}, Modifier.fillMaxSize())
        } }
        compose.mainClock.autoAdvance = false
        transition(wonEffectFixture())
        compose.onNodeWithTag("result-confetti").assertExists()
        // The restoration helper removes/re-adds content using waitForIdle; let those frames run.
        compose.mainClock.autoAdvance = true
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("result-confetti").assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReducedMotionResultTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })
    @Test fun disabledSystemAnimationsSuppressBothBursts() {
        var game by mutableStateOf(Tutorial.game(0))
        compose.setContent { MinescapeTheme(ThemeMode.LIGHT) { Board(game, {}, {}, Modifier.fillMaxSize()) } }
        compose.mainClock.autoAdvance = false
        listOf(wonEffectFixture(), lostEffectFixture()).forEach { result ->
            compose.runOnIdle { game = Tutorial.game(0); Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle { game = result; Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithTag("result-confetti").assertDoesNotExist()
            compose.onNodeWithTag("result-explosion").assertDoesNotExist()
        }
    }
}
