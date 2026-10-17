package com.cbulock.minescape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cbulock.minescape.data.*
import com.cbulock.minescape.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DailyChallengesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val records = (1..24).associate { day ->
        val date = "2026-09-${day.toString().padStart(2, '0')}"
        date to DailyRecord(date, if (day % 2 == 0) DailyOutcome.WON else DailyOutcome.LOST, 126000)
    }
    private var selected = ""
    private fun show(empty: Boolean = false, dark: Boolean = false) {
        compose.setContent { MinescapeTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            DailyChallengesScreen(AppData(dailyRecords = if (empty) emptyMap() else records), "2026-10-10", { selected = it }, {}, {})
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
    fun phoneHistorySharesAndPaginates() {
        show()
        capture("daily-phone")
        compose.onNodeWithText("Play today's challenge").performClick()
        assertEquals("2026-10-10", selected)
        compose.onNodeWithTag("daily-list").performScrollToKey("2026-09-24")
        capture("daily-history")
        compose.onAllNodesWithText("Share result")[0].performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Share image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Close").performClick()
        compose.onAllNodesWithText("Practice")[0].performClick()
        assertEquals("2026-09-24", selected)
        compose.onNodeWithTag("daily-list").performScrollToKey("more")
        compose.onNodeWithText("Show more (14 remaining)").performClick()
        compose.onNodeWithTag("daily-list").performScrollToKey("2026-09-05")
        compose.onNodeWithTag("daily-list").performScrollToKey("more")
        compose.onNodeWithText("Show more (4 remaining)").assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun emptyDark() {
        show(empty = true, dark = true)
        compose.onNodeWithText("A fresh challenge every day").assertIsDisplayed()
        compose.onNodeWithText("Share result").assertDoesNotExist()
        capture("daily-empty-dark")
    }
    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun largeTextActionsRemainReachable() {
        RuntimeEnvironment.setFontScale(2f)
        show()
        compose.onNodeWithTag("daily-list").performScrollToKey("2026-09-24")
        compose.onAllNodesWithText("Share result")[0].assertIsDisplayed()
        compose.onAllNodesWithText("Practice")[0].assertIsDisplayed()
        capture("daily-large-text")
    }
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun landscapeDark() {
        show(dark = true)
        compose.onNodeWithTag("daily-list").performScrollToKey("2026-09-24")
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onAllNodesWithText("Share result")[0].assertIsDisplayed()
        capture("daily-landscape-dark")
    }
}
