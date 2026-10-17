package com.cbulock.minescape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
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

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ResultEffectsLayoutTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })
    private fun render(name: String, win: Boolean, dark: Boolean = false) {
        val engine = GameEngine(kotlin.random.Random(3))
        val playing = engine.reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
        var game by mutableStateOf(playing)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            MinescapeTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface { Board(game, {}, {}, Modifier.fillMaxSize()) }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            game = if (win) {
                var result = playing
                playing.cells.indices.filter { !playing.cells[it].mine }.forEach { result = engine.reveal(result, it) }
                result
            } else engine.reveal(playing, playing.cells.indices.filter { playing.cells[it].mine }.minBy {
                kotlin.math.abs(it % 9 - 4) + kotlin.math.abs(it / 9 - 4)
            })
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(if (win) 850 else 200)
        compose.waitForIdle()
        compose.onNodeWithTag(if (win) "result-confetti" else "result-explosion").assertExists()
        compose.onNodeWithText("Fit board").assertIsDisplayed()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File("build/outputs/screenshots").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun confettiPhone() = render("result-confetti", win = true)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun explosionDarkPhone() = render("result-explosion", win = false, dark = true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi") fun confettiLandscape() = render("result-confetti-landscape", win = true)
}
