package com.camer.minesweeper

import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.camer.minesweeper.data.*
import com.camer.minesweeper.ui.DailyShare
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShareProviderTest {
    @Test fun realAndroidProviderReadsTheCardAndRejectsOtherCacheFiles() {
        // AndroidX FileProvider requires Android path separators; Windows JVM tests use a shadow.
        assumeTrue("Run the real FileProvider boundary on an Android device", File.separatorChar == '/')
        val context = ApplicationProvider.getApplicationContext<MinesweeperApplication>()
        val intent = DailyShare.intent(context, DailyRecord("2026-10-09", DailyOutcome.LOST, 45000))
        val bitmap = context.contentResolver.openInputStream(intent.clipData!!.getItemAt(0).uri)!!.use { BitmapFactory.decodeStream(it) }
        assertEquals(1080, bitmap.width); assertEquals(720, bitmap.height); bitmap.recycle()
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, "${context.packageName}.files", File(context.cacheDir, "private-progress.json"))
        }
    }
}
