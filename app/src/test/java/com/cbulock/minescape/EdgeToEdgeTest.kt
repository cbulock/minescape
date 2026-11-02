package com.cbulock.minescape

import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import com.cbulock.minescape.data.AppData
import com.cbulock.minescape.data.GameRepository
import com.cbulock.minescape.data.Preferences
import com.cbulock.minescape.data.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], qualifiers = "w640dp-h360dp-land-xhdpi")
class EdgeToEdgeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun homeKeepsControlsInsideSystemBarsAndSideInsets() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10000) {
                compose.onAllNodesWithContentDescription("Open menu").fetchSemanticsNodes().isNotEmpty()
            }
            // Inject a landscape side navigation bar and a top status bar. Removing the
            // app's safeDrawingPadding must cause this test to fail.
            scenario.onActivity { activity ->
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 48, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(96, 0, 96, 0))
                    .setVisible(WindowInsetsCompat.Type.systemBars(), true)
                    .build()
                ViewCompat.dispatchApplyWindowInsets(activity.findViewById<View>(android.R.id.content), insets)
            }
            compose.waitForIdle()
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val menu = compose.onNodeWithContentDescription("Open menu").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Menu overlaps the status bar: $menu", menu.top >= 48f)
            assertTrue("Menu overlaps the side navigation bar: $menu in $root", menu.right <= root.right - 96f)
            val title = compose.onNodeWithText("Minescape").fetchSemanticsNode().boundsInRoot
            assertTrue("Title overlaps the left inset: $title", title.left >= 96f)
        }
    }

    @Test fun systemBarIconsFollowAppThemeEvenWhenDeviceThemeDiffers() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (theme in listOf(ThemeMode.DARK, ThemeMode.LIGHT)) {
                scenario.onActivity { activity ->
                    runBlocking { GameRepository(activity).update { AppData(preferences = Preferences(theme = theme)) } }
                }
                compose.waitUntil(10000) {
                    var matches = false
                    scenario.onActivity { activity ->
                        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                        val light = theme == ThemeMode.LIGHT
                        matches = controller.isAppearanceLightStatusBars == light &&
                            controller.isAppearanceLightNavigationBars == light
                    }
                    matches
                }
            }
        }
    }
}
