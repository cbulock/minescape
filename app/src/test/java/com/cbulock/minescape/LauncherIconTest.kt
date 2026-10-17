package com.cbulock.minescape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LauncherIconTest {
    @Test fun launcherArtworkRendersWithinItsMask() {
        val context = RuntimeEnvironment.getApplication()
        val icon = context.getDrawable(context.applicationInfo.icon) as AdaptiveIconDrawable
        for (size in listOf(48, 512)) {
            icon.setBounds(0, 0, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            icon.draw(Canvas(bitmap))
            assertTrue("The icon center must render", bitmap.getPixel(size / 2, size / 2) ushr 24 > 0)
            val folder = File("build/outputs/screenshots").apply { mkdirs() }
            File(folder, "launcher-icon-$size.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val monochrome = requireNotNull(icon.monochrome)
        monochrome.setBounds(0, 0, 108, 108)
        val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        monochrome.draw(Canvas(bitmap))
        assertTrue("The themed icon must contain visible artwork",
            (0 until 108).any { x -> (0 until 108).any { y -> bitmap.getPixel(x, y) ushr 24 > 0 } })
        File("build/outputs/screenshots/launcher-icon-monochrome.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
