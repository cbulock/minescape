package com.cbulock.minescape

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.cbulock.minescape.game.*
import com.cbulock.minescape.ui.rememberResultPresentation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ResultPresentationTest {
    private var animationScale = 1f
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor get() = animationScale
    })
    private var game by mutableStateOf(Tutorial.game(0))
    private var enabled by mutableStateOf(true)
    @Composable private fun Content() {
        val result = rememberResultPresentation(game, enabled)
        if (result.open) Button(onClick = result.dismiss) { Text("View board") }
        else Button(onClick = result.show) { Text("View result") }
    }
    private fun launch() {
        compose.setContent { Content() }
        compose.mainClock.autoAdvance = false
    }
    private fun change(next: Game) {
        compose.runOnIdle { game = next; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(64)
    }
    @Test fun freshWinWaitsForCelebrationThenOpensAndStaysDismissed() {
        launch()
        change(wonEffectFixture())
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("View board").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithText("View board").performClick()
        compose.mainClock.advanceTimeBy(4000)
        compose.onNodeWithText("View board").assertDoesNotExist()
        compose.onNodeWithText("View result").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("View board").assertExists()
    }
    @Test fun lossOpensAfterExplosionAndUndoCanProduceAnotherResult() {
        launch()
        change(lostEffectFixture())
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("View board").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText("View board").assertExists()
        change(GameEngine().undo(game))
        compose.onNodeWithText("View board").assertDoesNotExist()
        change(GameEngine().act(game, 0, CellAction.REVEAL))
        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithText("View board").assertExists()
    }
    @Test fun pendingResultWaitsWhileMenuOrBackgroundIsActive() {
        launch()
        change(lostEffectFixture())
        compose.runOnIdle { enabled = false; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithText("View board").assertDoesNotExist()
        compose.runOnIdle { enabled = true; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithText("View board").assertExists()
    }
    @Test fun newGameCancelsAPendingResult() {
        launch()
        change(wonEffectFixture())
        change(Tutorial.game(0).copy(id = "next-game"))
        compose.mainClock.advanceTimeBy(4000)
        compose.onNodeWithText("View board").assertDoesNotExist()
    }
    @Test fun reducedMotionOpensWithoutTheCelebrationDelay() {
        animationScale = 0f
        launch()
        change(wonEffectFixture())
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("View board").assertExists()
    }
    @Test fun dismissedCompletedGameStaysDismissedAfterRestoration() {
        game = lostEffectFixture()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        compose.onNodeWithText("View board").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("View board").assertDoesNotExist()
    }
}
