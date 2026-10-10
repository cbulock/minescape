package com.camer.minesweeper

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.camer.minesweeper.data.*
import com.camer.minesweeper.game.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var host: ActivityScenario<MainActivity>
    private val localRuntime get() = android.os.Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: GameRepository
    private fun saved() = runBlocking { repository.data.first() }
    private fun awaitSave(condition: (AppData) -> Boolean) {
        compose.waitUntil(10000) { compose.waitForIdle(); condition(saved()) }
    }
    private fun advanceTime(millis: Long) {
        if (localRuntime) compose.mainClock.advanceTimeBy(millis) else Thread.sleep(millis)
        compose.waitForIdle()
    }
    private fun menu(label: String) {
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onAllNodesWithText(label).onLast().performClick()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        var rendered: Bitmap? = null
        if (localRuntime) host.onActivity { activity ->
            val view = activity.window.decorView
            rendered = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        val bitmap = rendered ?: compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        val folder = if (localRuntime) File("build/outputs/screenshots") else context.getExternalFilesDir(null)!!
        folder.mkdirs()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun newGameSaveRestoreSettingsAndRotation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            host = scenario
            scenario.onActivity { activity -> repository = GameRepository(activity); runBlocking { repository.update { AppData() } } }
            compose.waitUntil(10000) { compose.waitForIdle(); compose.onAllNodesWithText("Beginner").fetchSemanticsNodes().isNotEmpty() }
            screenshot("01-home")
            compose.onNodeWithText("Statistics").performScrollTo().performClick()
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("Beginner").performScrollTo()
            compose.onNodeWithText("Beginner").performClick()
            awaitSave { it.game != null }
            compose.onNodeWithTag("cell-40").performTouchInput { click() }
            awaitSave { it.game?.status != GameStatus.READY }
            val original = saved().game!!
            assertFalse(original.cells[40].mine)
            compose.onNodeWithContentDescription("Flag mode").assertIsOff().performClick()
            awaitSave { it.game?.flagMode == true }
            compose.onNodeWithContentDescription("Flag mode").assertIsOn().performClick()
            awaitSave { it.game?.flagMode == false }
            screenshot("02-playing")
            menu("Settings")
            compose.onNodeWithText("Dark").performClick()
            compose.onNodeWithText("Done").performClick()
            awaitSave { it.preferences.theme == ThemeMode.DARK }
            screenshot("03-dark")
            assertEquals("Opening settings must preserve the board", original.cells, saved().game!!.cells)
            val diskSave = runBlocking { context.filesDir.resolve("datastore/minesweeper.json").inputStream().use { AppDataSerializer.readFrom(it) } }
            assertEquals("The generated board must reach disk", original.cells, diskSave.game!!.cells)
            repeat(5) {
                scenario.recreate()
                compose.waitUntil(5000) { compose.waitForIdle(); compose.onAllNodesWithTag("board").fetchSemanticsNodes().isNotEmpty() }
                compose.waitForIdle()
                val restored = saved().game!!
                assertEquals("Recreation must keep the saved game identity", original.id, restored.id)
                assertEquals("Recreation must keep all cells", original.cells, restored.cells)
            }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitForIdle()
            screenshot("04-landscape")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitForIdle()
            menu("New game")
            compose.onNodeWithText("Back to current game").performClick()
            compose.onNodeWithTag("board").assertIsDisplayed()
            assertEquals(original.id, saved().game!!.id)
            menu("New game")
            compose.onNodeWithText("Expert").performScrollTo().performClick()
            compose.onNodeWithText("Replace current game?").assertExists()
            compose.onNodeWithText("Keep playing").performClick()
            compose.onNodeWithTag("board").assertExists()
            assertEquals(original.id, saved().game!!.id)
            menu("Statistics")
            screenshot("05-stats")
            val pausedTime = saved().game!!.elapsedMillis
            advanceTime(1400)
            assertEquals(pausedTime, saved().game!!.elapsedMillis)
            compose.onNodeWithText("Done").performClick()
            scenario.moveToState(Lifecycle.State.CREATED)
            advanceTime(200)
            val backgroundTime = saved().game!!.elapsedMillis
            advanceTime(1400)
            assertEquals(backgroundTime, saved().game!!.elapsedMillis)
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }

    @Test fun customValidationAndMaximumField() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            host = scenario
            scenario.onActivity { activity -> repository = GameRepository(activity); runBlocking { repository.update { AppData() } } }
            compose.waitUntil(10000) { compose.waitForIdle(); compose.onAllNodesWithText("Make a custom board").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Make a custom board").performScrollTo().performClick()
            compose.onNodeWithText("Columns").performScrollTo().performTextReplacement("4")
            compose.onNodeWithText("Play custom board").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Columns").performScrollTo().performTextReplacement("50")
            compose.onNodeWithText("Rows").performScrollTo().performTextReplacement("50")
            compose.onNodeWithText("Mines").performScrollTo().performTextReplacement("875")
            compose.onNodeWithText("Play custom board").performScrollTo().performClick()
            awaitSave { it.game?.config?.size == 2500 }
            compose.onNodeWithTag("cell-2499").assertExists()
            screenshot("06-maximum")
            compose.onNodeWithContentDescription("Zoom in").performClick()
            awaitSave { (it.game?.viewport?.cellDp ?: 0f) > 0f }
            compose.onNodeWithText("Fit board").performClick()
        }
    }
}
