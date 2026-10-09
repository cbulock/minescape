package com.camer.minesweeper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.camer.minesweeper.data.ThemeMode
import com.camer.minesweeper.game.*
import com.camer.minesweeper.ui.Board
import com.camer.minesweeper.ui.MinesweeperTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class BoardGestureTest {
    @Test fun customHoldThresholdAndQuestionCycleNeverRevealMine() {
        var field = Tutorial.game(0).copy(viewport = Viewport(48f, 2.5f, 2.5f))
        compose.setContent {
            var game by remember { mutableStateOf(field) }
            MinesweeperTheme(ThemeMode.LIGHT) {
                Board(game, onCell = { intents.add(it); game = GameEngine().act(game, it.index, it.action, true); field = game },
                    onViewport = {}, modifier = Modifier.fillMaxSize(), holdMillis = 800L, questionMarks = true)
            }
        }
        repeat(3) {
            compose.onNodeWithTag("cell-0").performTouchInput { down(center); advanceEventTime(800); up() }
            compose.runOnIdle {
                assertFalse(field.cells[0].revealed); assertEquals(GameStatus.PLAYING, field.status)
                assertEquals(it == 0, field.cells[0].flagged); assertEquals(it == 1, field.cells[0].questioned)
            }
        }
        compose.runOnIdle { assertEquals(3, intents.size); assertTrue(intents.all { it.action == CellAction.TOGGLE_FLAG }) }
    }
    @Test fun controlPreferenceChangeCancelsPendingPressAndDisablesTapChord() {
        var duration by mutableStateOf(300L)
        var tapChord by mutableStateOf(true)
        val field = Tutorial.game(2).copy(viewport = Viewport(48f, 2.5f, 2.5f))
        compose.setContent { MinesweeperTheme(ThemeMode.LIGHT) { Board(field, onCell = { intents.add(it) }, onViewport = {},
            modifier = Modifier.fillMaxSize(), holdMillis = duration, tapToChord = tapChord) } }
        compose.onNodeWithTag("cell-0").performTouchInput { down(center); advanceEventTime(100) }
        compose.runOnIdle { duration = 800L; tapChord = false }
        compose.onNodeWithTag("cell-0").performTouchInput { advanceEventTime(900); up() }
        compose.onNodeWithTag("cell-6").performTouchInput { click() }
        compose.onNodeWithTag("cell-6").performTouchInput { longClick(durationMillis = 900) }
        compose.runOnIdle { assertTrue(intents.isEmpty()) }
        compose.runOnIdle { tapChord = true }
        compose.onNodeWithTag("cell-6").performTouchInput { click() }
        compose.runOnIdle { assertEquals(CellAction.CHORD, intents.single().action) }
    }
    @get:Rule val compose = createComposeRule()
    private var taps = 0
    private var holds = 0
    private val intents = mutableListOf<CellIntent>()
    private var lastViewport: Viewport? = null
    private fun launch(config: BoardConfig = BoardConfig.preset(Difficulty.BEGINNER), zoomed: Boolean = false) {
        compose.setContent {
            MinesweeperTheme(ThemeMode.LIGHT) {
                Board(Game(config).let { if (zoomed) it.copy(viewport = Viewport(48f, config.columns / 2f, config.rows / 2f)) else it }, onCell = { intents.add(it); if (it.action == CellAction.TOGGLE_FLAG) holds++ else taps++ }, onViewport = { lastViewport = it }, modifier = Modifier.fillMaxSize())
            }
        }
    }
    @Test fun tapRevealsExactlyOnce() {
        launch()
        compose.onNodeWithTag("cell-40").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, taps); assertEquals(0, holds) }
    }
    @Test fun longPressFlagsWithoutRevealing() {
        launch()
        compose.onNodeWithTag("cell-40").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(0, taps); assertEquals(1, holds) }
    }
    @Test fun repeatedHoldsRedrawFlagRemovalImmediately() = checkFlagRemoval(flagMode = false)
    @Test fun repeatedFlagModeTapsRedrawFlagRemovalImmediately() = checkFlagRemoval(flagMode = true)

    private fun checkFlagRemoval(flagMode: Boolean) {
        lateinit var view: View
        var game by mutableStateOf(Game(BoardConfig.preset(Difficulty.BEGINNER),
            flagMode = flagMode, viewport = Viewport(48f, 4.5f, 4.5f)))
        compose.setContent {
            view = LocalView.current
            MinesweeperTheme(ThemeMode.LIGHT) {
                Board(game, onCell = {
                    intents.add(it); game = GameEngine().act(game, it.index, it.action)
                }, onViewport = {}, modifier = Modifier.fillMaxSize())
            }
        }
        val tile = compose.onNodeWithTag("cell-40")
        fun flagPixel(): Int {
            val bounds = tile.fetchSemanticsNode().boundsInRoot
            var pixel = 0
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    pixel = bitmap.getPixel((bounds.left + bounds.width * .55f).toInt(),
                        (bounds.top + bounds.height * .3f).toInt())
                } finally { bitmap.recycle() }
            }
            return pixel
        }
        val covered = flagPixel()
        repeat(2) {
            tile.performTouchInput { if (flagMode) click() else longClick() }
            tile.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Flagged"))
            assertNotEquals("Flag must be drawn after placing it", covered, flagPixel())
            tile.performTouchInput { if (flagMode) click() else longClick() }
            tile.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Covered"))
            assertEquals("Flag must disappear before touching another tile", covered, flagPixel())
        }
        compose.runOnIdle {
            assertFalse(game.cells[40].revealed)
            assertEquals(4, intents.size)
            assertTrue(intents.all { it.index == 40 && it.action == CellAction.TOGGLE_FLAG })
        }
    }
    @Test fun dragNeverActivatesACell() {
        launch(BoardConfig.preset(Difficulty.EXPERT))
        compose.onNodeWithContentDescription("Zoom in").performClick()
        compose.onNodeWithTag("board").performTouchInput { swipe(center, center + Offset(100f, 20f), durationMillis = 400) }
        compose.runOnIdle { assertEquals(0, taps); assertEquals(0, holds); assertNotNull(lastViewport) }
    }
    @Test fun pinchNeverActivatesACell() {
        launch(BoardConfig.preset(Difficulty.EXPERT))
        compose.onNodeWithTag("board").performTouchInput {
            down(0, center - Offset(30f, 0f)); down(1, center + Offset(30f, 0f))
            moveTo(0, center - Offset(90f, 0f), delayMillis = 150)
            moveTo(1, center + Offset(90f, 0f), delayMillis = 150)
            up(0); up(1)
        }
        compose.runOnIdle { assertEquals(0, taps); assertEquals(0, holds); assertNotNull(lastViewport) }
    }
    @Test fun coveredCellsExposeNoMineInformationAndSupportAccessibility() {
        launch()
        compose.onNodeWithTag("cell-40").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Covered"))
        compose.onNodeWithTag("cell-40").performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }
    @Test fun maximumBoardRendersAndZoomControlsWork() {
        launch(BoardConfig(50, 50, 875))
        compose.onNodeWithTag("cell-2499").assertExists()
        compose.onNodeWithContentDescription("Zoom in").performClick()
        compose.onNodeWithContentDescription("Zoom out").performClick()
        compose.onNodeWithText("Fit board").performClick()
        compose.runOnIdle { assertEquals(0f, lastViewport!!.cellDp) }
    }
    @Test fun zoomOutButtonAndPinchCannotShrinkBelowFit() {
        launch()
        compose.onNodeWithContentDescription("Zoom out").performClick()
        var fit = 0f
        compose.runOnIdle { fit = lastViewport!!.cellDp; assertTrue(fit > 24f) }
        compose.onNodeWithContentDescription("Zoom in").performClick()
        repeat(4) { compose.onNodeWithContentDescription("Zoom out").performClick() }
        compose.runOnIdle { assertEquals(fit, lastViewport!!.cellDp) }
        compose.onNodeWithTag("board").performTouchInput {
            down(0, center - Offset(80f, 0f)); down(1, center + Offset(80f, 0f))
            moveTo(0, center - Offset(10f, 0f), delayMillis = 150)
            moveTo(1, center + Offset(10f, 0f), delayMillis = 150)
            up(0); up(1)
        }
        compose.runOnIdle { assertEquals(fit, lastViewport!!.cellDp); assertTrue(intents.isEmpty()) }
    }
    @Test fun thresholdReleaseIsClassifiedByPointerTime() {
        launch(zoomed = true)
        for (duration in listOf(499L, 500L, 501L)) {
            compose.onNodeWithTag("cell-40").performTouchInput { down(center); advanceEventTime(duration); up() }
        }
        compose.runOnIdle {
            assertEquals(listOf(CellAction.REVEAL, CellAction.TOGGLE_FLAG, CellAction.TOGGLE_FLAG), intents.map { it.action })
            assertTrue(intents.all { it.index == 40 })
        }
    }
    @Test fun crossingAdjacentCellBelowSlopNeverChangesTarget() {
        launch(zoomed = true)
        compose.onNodeWithTag("cell-40").performTouchInput {
            val edge = Offset(width.toFloat() - 1f, center.y)
            down(edge); moveTo(edge + Offset(3f, 0f), delayMillis = 50); moveTo(edge, delayMillis = 50); up()
        }
        compose.runOnIdle { assertTrue(intents.isEmpty()) }
    }
    @Test fun cancelledPressNeverActivates() {
        launch(zoomed = true)
        compose.onNodeWithTag("cell-40").performTouchInput { down(center); advanceEventTime(100); cancel() }
        compose.runOnIdle { assertTrue(intents.isEmpty()) }
    }
    @Test fun holdThenDriftAndLiftFlagsOnlyOnce() {
        launch(zoomed = true)
        compose.onNodeWithTag("cell-40").performTouchInput {
            down(center); advanceEventTime(700); moveTo(center + Offset(1f, 0f)); advanceEventTime(1000); up()
        }
        compose.runOnIdle { assertEquals(0, taps); assertEquals(1, holds); assertEquals(40, intents.single().index) }
    }
    @Test fun staggeredSecondFingerAndPrimaryLiftNeverActivate() {
        launch(zoomed = true)
        compose.onNodeWithTag("board").performTouchInput {
            down(0, center); advanceEventTime(100); down(1, center + Offset(30f, 0f)); up(0)
            moveTo(1, center + Offset(70f, 0f), delayMillis = 50); advanceEventTime(700); up(1)
        }
        compose.runOnIdle { assertTrue(intents.isEmpty()) }
    }
    @Test fun tinyCellHoldOnlyZoomsAndFreshGestureFlags() {
        launch(BoardConfig(50, 50, 875))
        compose.onNodeWithTag("cell-1275").performTouchInput { longClick() }
        compose.runOnIdle { assertTrue(intents.isEmpty()); assertEquals(32f, lastViewport!!.cellDp) }
        compose.onNodeWithTag("cell-1275").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(CellAction.TOGGLE_FLAG, intents.single().action); assertEquals(1275, intents.single().index) }
    }
    @Test fun holdingKnownMineNeverLosesOrReveals() {
        val config = BoardConfig.preset(Difficulty.BEGINNER)
        val mines = setOf(40, 0, 1, 2, 3, 4, 5, 6, 7, 8)
        var field = Game(config, status = GameStatus.PLAYING, viewport = Viewport(48f, 4.5f, 4.5f),
            cells = List(81) { Cell(mine = it in mines, adjacent = GameEngine.neighbors(config, it).count { i -> i in mines }) })
        compose.setContent {
            var game by remember { mutableStateOf(field) }
            MinesweeperTheme(ThemeMode.LIGHT) { Board(game, onCell = {
                intents.add(it); game = GameEngine().act(game, it.index, it.action); field = game
            }, onViewport = {}, modifier = Modifier.fillMaxSize()) }
        }
        compose.onNodeWithTag("cell-40").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(GameStatus.PLAYING, field.status); assertTrue(field.cells[40].flagged)
            assertFalse(field.cells[40].revealed); assertEquals(CellAction.TOGGLE_FLAG, intents.single().action)
        }
    }

    @Test fun disablingInputMidPressCancelsUntilFreshGesture() {
        var enabled by mutableStateOf(true)
        val game = Game(BoardConfig.preset(Difficulty.BEGINNER), viewport = Viewport(48f, 4.5f, 4.5f))
        compose.setContent { MinesweeperTheme(ThemeMode.LIGHT) { Board(game, onCell = { intents.add(it) }, onViewport = {}, modifier = Modifier.fillMaxSize(), inputEnabled = enabled) } }
        compose.onNodeWithTag("cell-40").performTouchInput { down(center); advanceEventTime(100) }
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("cell-40").performTouchInput { advanceEventTime(700); up() }
        compose.runOnIdle { assertTrue(intents.isEmpty()); enabled = true }
        compose.onNodeWithTag("cell-40").performTouchInput { click() }
        compose.runOnIdle { assertEquals(CellAction.REVEAL, intents.single().action) }
    }
    @Test fun modeAndGameReplacementCancelPendingHold() {
        var game by mutableStateOf(Game(BoardConfig.preset(Difficulty.BEGINNER), viewport = Viewport(48f, 4.5f, 4.5f)))
        compose.setContent { MinesweeperTheme(ThemeMode.LIGHT) { Board(game, onCell = { intents.add(it) }, onViewport = {}, modifier = Modifier.fillMaxSize()) } }
        compose.onNodeWithTag("cell-40").performTouchInput { down(center); advanceEventTime(100) }
        compose.runOnIdle { game = game.copy(flagMode = true) }
        compose.onNodeWithTag("cell-40").performTouchInput { advanceEventTime(700); up() }
        compose.runOnIdle { assertTrue(intents.isEmpty()) }
        compose.onNodeWithTag("cell-40").performTouchInput { down(center); advanceEventTime(100) }
        compose.runOnIdle { game = game.copy(id = "replacement") }
        compose.onNodeWithTag("cell-40").performTouchInput { advanceEventTime(700); up() }
        compose.runOnIdle { assertTrue(intents.isEmpty()) }
    }
    @Test fun layoutAndExternalViewportChangeCancelPendingHold() {
        var extent by mutableStateOf(240)
        var game by mutableStateOf(Game(BoardConfig.preset(Difficulty.BEGINNER), viewport = Viewport(48f, 4.5f, 4.5f)))
        compose.setContent { MinesweeperTheme(ThemeMode.LIGHT) { Board(game, onCell = { intents.add(it) }, onViewport = {}, modifier = Modifier.width(extent.dp).height(400.dp)) } }
        compose.onNodeWithTag("cell-40").performTouchInput { down(center); advanceEventTime(100) }
        compose.runOnIdle { extent = 300 }
        compose.onNodeWithTag("cell-40").performTouchInput { advanceEventTime(700); up() }
        compose.runOnIdle { assertTrue(intents.isEmpty()) }
        compose.onNodeWithTag("cell-40").performTouchInput { down(center); advanceEventTime(100) }
        compose.runOnIdle { game = game.copy(viewport = Viewport(52f, 4.5f, 4.5f)) }
        compose.onNodeWithTag("cell-40").performTouchInput { advanceEventTime(700); up() }
        compose.runOnIdle { assertTrue(intents.isEmpty()) }
    }

}
