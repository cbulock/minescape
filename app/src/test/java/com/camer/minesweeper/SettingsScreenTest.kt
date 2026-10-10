package com.camer.minesweeper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.camer.minesweeper.data.*
import com.camer.minesweeper.ui.MinesweeperTheme
import com.camer.minesweeper.ui.SettingsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private var preferences by mutableStateOf(Preferences(theme = ThemeMode.LIGHT))
    private var exports = 0
    private var imports = 0
    private var closed = false

    private fun show(dark: Boolean = false) {
        preferences = preferences.copy(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        compose.setContent { MinesweeperTheme(preferences.theme) {
            SettingsScreen(preferences, { preferences = it }, { closed = true }, { exports++ }, { imports++ })
        } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last { it.isShown && it.width > 0 }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File("build/outputs/screenshots").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun phoneControlsAndBackups() {
        show()
        capture("settings-phone")
        compose.onNodeWithText("Dark").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Haptic feedback").performClick().assertIsOff()
        compose.onNodeWithContentDescription("Question marks").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Tap to chord").performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText("800 ms").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(ThemeMode.DARK, preferences.theme)
            assertFalse(preferences.haptics)
            assertTrue(preferences.questionMarks)
            assertFalse(preferences.tapToChord)
            assertEquals(800L, preferences.holdMillis)
        }
        capture("settings-controls-dark")
        compose.onNodeWithText("Export progress").performScrollTo().performClick()
        compose.onNodeWithText("Import progress").performScrollTo().performClick()
        compose.onNodeWithText("Changes save automatically.").performScrollTo()
        capture("settings-backups-dark")
        compose.onNodeWithText("Done").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, exports); assertEquals(1, imports); assertTrue(closed) }
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun darkPhone() {
        show(dark = true)
        compose.onNodeWithText("Dark").assertIsSelected()
        capture("settings-dark")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun largeTextKeepsAllControlsReachable() {
        RuntimeEnvironment.setFontScale(2f)
        show()
        capture("settings-large-text")
        val root = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        listOf("Haptic feedback", "Question marks", "Tap to chord").forEach { label ->
            val node = compose.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            check(bounds.left >= root.left && bounds.right <= root.right) { "$label exceeds the screen width" }
            node.performClick()
        }
        capture("settings-large-text-controls")
        listOf("Device default", "300 ms", "500 ms", "800 ms", "1200 ms", "Export progress", "Import progress").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Done").assertIsDisplayed()
        capture("settings-large-text-backup")
    }

    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun landscape() {
        show()
        compose.onNodeWithText("800 ms").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        capture("settings-landscape")
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tabletUsesComfortableReadingWidth() {
        show()
        val content = compose.onNodeWithTag("settings-scroll").fetchSemanticsNode().boundsInRoot
        val root = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        check(content.width < root.width * .9f)
        capture("settings-tablet")
    }
}
