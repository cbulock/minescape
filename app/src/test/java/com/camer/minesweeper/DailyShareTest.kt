package com.camer.minesweeper

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.camer.minesweeper.data.*
import com.camer.minesweeper.game.*
import com.camer.minesweeper.ui.DailyShare
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [WindowsShareProviderShadow::class])
class DailyShareTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun shareCardHasReadableImageAndNarrowGrantedUriWithNoSpoilers() {
        val context = ApplicationProvider.getApplicationContext<MinesweeperApplication>()
        val record = DailyRecord("2026-10-09", DailyOutcome.WON, 87000)
        val intent = DailyShare.intent(context, record)
        assertEquals(Intent.ACTION_SEND, intent.action); assertEquals("image/png", intent.type)
        assertEquals(DailyShare.text(record, context.resources.configuration.locales[0]), intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
        val uri = intent.clipData!!.getItemAt(0).uri
        assertEquals("content", uri.scheme)
        val provider = context.packageManager.resolveContentProvider(uri.authority!!, 0)!!
        assertFalse(provider.exported); assertTrue(provider.grantUriPermissions)
        val file = File(context.cacheDir, "daily-shares/${uri.lastPathSegment}")
        assertNotEquals(uri, DailyShare.intent(context, record).clipData!!.getItemAt(0).uri)
        val bitmap = file.inputStream().use { BitmapFactory.decodeStream(it) }
        assertEquals(1080, bitmap.width); assertEquals(720, bitmap.height); bitmap.recycle()
        val output = File("build/outputs/screenshots/daily-share-card.png").apply { parentFile!!.mkdirs() }
        file.copyTo(output, overwrite = true)
        assertThrows(IllegalArgumentException::class.java) { DailyShare.text(record.copy(outcome = DailyOutcome.IN_PROGRESS)) }
    }
    @Test fun localizedDatesKeepThePuzzleDayAcrossTimeZones() {
        val record = DailyRecord("2026-10-09", DailyOutcome.WON, 87000)
        val originalZone = TimeZone.getDefault()
        try {
            for (zone in listOf("Pacific/Kiritimati", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals("Minescape Daily · Oct 9, 2026\nWon · 01:27\nFirst attempt · Intermediate · No-guess",
                    DailyShare.text(record, Locale.US))
                assertTrue(DailyShare.text(record, Locale.GERMANY).startsWith("Minescape Daily · 09.10.2026\n"))
                assertFalse(DailyShare.text(record, Locale.JAPAN).contains("UTC"))
            }
        } finally { TimeZone.setDefault(originalZone) }
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun renderOutcomeAndLocalePreviews() {
        for ((name, outcome, locale) in listOf(
            Triple("won", DailyOutcome.WON, Locale.US),
            Triple("lost", DailyOutcome.LOST, Locale.GERMANY),
            Triple("japanese", DailyOutcome.WON, Locale.JAPAN),
        )) {
            val bitmap = DailyShare.card(DailyRecord("2026-10-09", outcome, 87000), locale)
            try {
                val file = File("build/outputs/screenshots/daily-share-$name.png").apply { parentFile!!.mkdirs() }
                file.outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
    }
    @Test fun officialResultButtonOpensAndroidChooserAndPracticeHasNoShareButton() = checkSharing(won = false)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun dailyWinHasVisibleShareAndPreview() = checkSharing(won = true)
    @Test fun dailyHistoryShowsTheSamePreview() = checkSharing(won = true, history = true)

    private fun checkSharing(won: Boolean, history: Boolean = false) {
        val challenge = DailyChallenge(Json.decodeFromString(File("src/main/assets/daily-v1.json").readText()))
        val original = challenge.game("2026-10-09")
        var finished = original
        if (won) original.cells.indices.filter { !original.cells[it].mine }.forEach { finished = GameEngine().reveal(finished, it) }
        else finished = GameEngine().reveal(original, original.cells.indexOfFirst { it.mine })
        val data = AppData(dailyGame = finished, activeSlot = ActiveSlot.DAILY,
            dailyRecords = mapOf("2026-10-09" to DailyRecord("2026-10-09", DailyOutcome.IN_PROGRESS))).recordResult()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { runBlocking { GameRepository(it).update { data } } }
            compose.waitUntil(10000) { compose.waitForIdle(); compose.onAllNodesWithText("Share result").fetchSemanticsNodes().isNotEmpty() }
            if (history) {
                compose.onNodeWithText("View board").performClick()
                compose.onNodeWithContentDescription("Open menu").performClick()
                compose.onNodeWithText("Daily challenges").performClick()
                compose.onNodeWithText("Share result").performScrollTo()
            }
            compose.onNodeWithText("Share result").assertIsDisplayed().performClick()
            compose.waitUntil(10000) {
                compose.waitForIdle()
                compose.onAllNodesWithContentDescription(DailyShare.text(data.dailyRecords["2026-10-09"]!!)).fetchSemanticsNodes().isNotEmpty()
            }
            if (won && !history) scenario.onActivity {
                val view = android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.isShown && it.width > 0 }
                val capture = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(capture))
                File("build/outputs/screenshots/daily-share-preview.png").apply { parentFile!!.mkdirs() }
                    .outputStream().use { capture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                capture.recycle()
            }
            compose.onNodeWithText("Share image").assertIsEnabled().performClick()
            var chooser: Intent? = null
            compose.waitUntil(10000) {
                compose.waitForIdle()
                compose.onNodeWithText("Could not share result").assertDoesNotExist()
                scenario.onActivity { if (chooser == null) chooser = Shadows.shadowOf(it).nextStartedActivity }
                chooser != null
            }
            assertEquals(Intent.ACTION_CHOOSER, chooser!!.action)
            val send = chooser!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(DailyShare.text(data.dailyRecords["2026-10-09"]!!), send.getStringExtra(Intent.EXTRA_TEXT))
            compose.onNodeWithText("Close").performClick()
            if (history) compose.onNodeWithText("Done").performClick()
            scenario.onActivity { runBlocking { GameRepository(it).update { data.copy(game = challenge.game("2026-10-09", true), activeSlot = ActiveSlot.REGULAR) } } }
            compose.waitUntil(10000) { compose.waitForIdle(); compose.onAllNodesWithText("Share result").fetchSemanticsNodes().isEmpty() }
        }
    }
}

// AndroidX's containment check uses '/', while Windows canonical paths use '\\'.
// Isolate that OS boundary in local tests; ShareProviderTest uses the real provider on Android.
@Implements(value = FileProvider::class, isInAndroidSdk = false)
class WindowsShareProviderShadow {
    companion object {
        @JvmStatic @Implementation
        fun getUriForFile(context: Context, authority: String, file: File): Uri {
            val root = File(context.cacheDir, "daily-shares").canonicalFile.toPath()
            require(file.canonicalFile.toPath().startsWith(root))
            require(authority == "${context.packageName}.files")
            context.resources.getXml(R.xml.share_paths).use { xml ->
                while (xml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT &&
                    !(xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xml.name == "cache-path")) xml.next()
                require(xml.name == "cache-path")
                require(xml.getAttributeValue(null, "name") == "daily-results")
                require(xml.getAttributeValue(null, "path") == "daily-shares/")
            }
            return Uri.Builder().scheme("content").authority(authority).appendPath("daily-results").appendPath(file.name).build()
        }
    }
}
