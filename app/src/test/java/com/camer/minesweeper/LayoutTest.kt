package com.camer.minesweeper

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.camer.minesweeper.data.*
import com.camer.minesweeper.game.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class LayoutTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun render(name: String, dark: Boolean = false, home: Boolean = false, end: GameStatus? = null) {
        val engine = GameEngine(Random(42))
        var game = engine.reveal(Game(BoardConfig.preset(Difficulty.INTERMEDIATE)), 119).copy(elapsedMillis = 84000)
        if (end == GameStatus.LOST) game = engine.reveal(game, game.cells.indexOfFirst { it.mine })
        if (end == GameStatus.WON) game.cells.indices.filter { !game.cells[it].mine }.forEach { game = engine.reveal(game, it) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking { GameRepository(activity).update { AppData(game = if (home) null else game, preferences = Preferences(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)).recordResult() } }
            }
            compose.waitUntil(10000) {
                compose.waitForIdle()
                if (home) compose.onAllNodesWithText("Beginner").fetchSemanticsNodes().isNotEmpty()
                else compose.onAllNodesWithTag("board").fetchSemanticsNodes().isNotEmpty()
            }
            if (!home) {
                if (end != null) compose.onNodeWithText("View board").performClick()
                compose.onNodeWithText("Fit board").assertIsDisplayed()
                compose.onNodeWithTag("board").assertIsDisplayed()
                val bounds = compose.onNodeWithTag("board").fetchSemanticsNode().boundsInRoot
                val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
                val minimumFraction = if (end == null) .65f else .5f
                check(bounds.height > screen.height * minimumFraction) { "Board must dominate the screen: $bounds in $screen" }
                check(bounds.width > screen.width * .9f) { "Board must use the screen width: $bounds in $screen" }
                if (end == null) compose.onNodeWithContentDescription("Flag mode").assertIsDisplayed()
                else compose.onNodeWithContentDescription("Flag mode").assertDoesNotExist()
                compose.onNodeWithContentDescription("Open menu").assertIsDisplayed()
            }
            compose.waitForIdle()
            scenario.onActivity { activity ->
                val view = activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val folder = File("build/outputs/screenshots").apply { mkdirs() }
                File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            if (home) {
                val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
                listOf("Beginner", "Intermediate", "Expert", "Make a custom board", "Daily challenge", "Learn to play", "Statistics").forEach { label ->
                    val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    check(bounds.left >= screen.left && bounds.right <= screen.right) { "$label exceeds screen width: $bounds in $screen" }
                }
                compose.onNodeWithContentDescription("Game options").performScrollTo().performClick()
                compose.onNodeWithContentDescription("Practice mode").performScrollTo().assertIsDisplayed()
                compose.onNodeWithContentDescription("No-guess presets").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Done").performClick()
            }
        }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun smallPhoneHome() = render("phone-home", home = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun darkHome() = render("phone-home-dark", home = true, dark = true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi") fun landscapeHome() = render("phone-home-landscape", home = true)
    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi") fun tabletHome() = render("tablet-home", home = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun largeTextHome() { RuntimeEnvironment.setFontScale(2f); render("phone-home-large-text", home = true) }
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun smallPhoneLight() = render("phone-light")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun smallPhoneDark() = render("phone-dark", dark = true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi") fun phoneLandscape() = render("phone-landscape")
    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi") fun tabletPortrait() = render("tablet-portrait")
    @Test @Config(qualifiers = "w1280dp-h800dp-land-xhdpi") fun tabletLandscape() = render("tablet-landscape", dark = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun largeText() { RuntimeEnvironment.setFontScale(2f); render("phone-large-text") }
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun largeTextFinished() { RuntimeEnvironment.setFontScale(2f); render("phone-large-text-finished", end = GameStatus.WON) }
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun wonGame() = render("phone-won", end = GameStatus.WON)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun lostGame() = render("phone-lost", end = GameStatus.LOST)
}
