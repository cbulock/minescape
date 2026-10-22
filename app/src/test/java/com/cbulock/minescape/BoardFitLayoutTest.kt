package com.cbulock.minescape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cbulock.minescape.data.ThemeMode
import com.cbulock.minescape.game.*
import com.cbulock.minescape.ui.Board
import com.cbulock.minescape.ui.MinescapeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BoardFitLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun render(name: String, portrait: Boolean, difficulty: Difficulty = Difficulty.EXPERT) {
        val config = BoardConfig.preset(difficulty)
        val game = Game(config)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            MinescapeTheme(ThemeMode.LIGHT) { Board(game, {}, {}, Modifier.fillMaxSize()) }
        }
        compose.onNodeWithText("Fit board").assertIsDisplayed()
        val first = compose.onNodeWithTag("cell-0").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("cell-${config.size - 1}").fetchSemanticsNode().boundsInRoot
        if (difficulty == Difficulty.BEGINNER) {
            check(first.width > 128f) { "Tablet cells should grow beyond the previous 64 dp cap" }
        } else check(if (portrait) last.bottom - first.top > last.right - first.left
            else last.right - first.left > last.bottom - first.top)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File("build/outputs/screenshots").apply { mkdirs() }
            File(folder, "board-fit-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun expertPortrait() = render("phone-portrait", true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi") fun expertLandscape() = render("phone-landscape", false)
    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi") fun expertTablet() = render("tablet-portrait", true)
    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi") fun beginnerTablet() = render("tablet-beginner", true, Difficulty.BEGINNER)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun transposedTouchTargetsLogicalCells() {
        val game = Game(BoardConfig.preset(Difficulty.EXPERT), viewport = Viewport(48f, 15f, 8f))
        val intents = mutableListOf<CellIntent>()
        compose.setContent { MinescapeTheme(ThemeMode.LIGHT) {
            Board(game, { intents.add(it) }, {}, Modifier.fillMaxSize())
        } }
        compose.onNodeWithTag("cell-255").performTouchInput { click() }
        compose.runOnIdle { check(intents.single().index == 255 && intents.single().action == CellAction.REVEAL) }
    }
}
