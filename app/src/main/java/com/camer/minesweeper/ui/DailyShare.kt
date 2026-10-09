package com.camer.minesweeper.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.camer.minesweeper.data.DailyOutcome
import com.camer.minesweeper.data.DailyRecord
import com.camer.minesweeper.game.formatTime
import java.io.File

object DailyShare {
    // Accept only the official record: no layout, coordinates, or practice assistance can leak.
    fun text(record: DailyRecord): String {
        require(record.outcome == DailyOutcome.WON || record.outcome == DailyOutcome.LOST)
        return "Minescape Daily · ${record.date} (UTC)\n${if (record.outcome == DailyOutcome.WON) "Won" else "Lost"} · ${formatTime(record.elapsedMillis)}\nFirst attempt · Intermediate · No-guess"
    }
    fun card(record: DailyRecord): Bitmap {
        text(record)
        val bitmap = createBitmap(1080, 720)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(28, 27, 40))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(184, 233, 215)
        canvas.drawRoundRect(64f, 64f, 1016f, 656f, 40f, 40f, paint)
        paint.color = Color.rgb(234, 230, 255)
        canvas.drawRoundRect(64f, 64f, 1016f, 296f, 40f, 40f, paint)
        fun line(value: String, y: Float, size: Float, bold: Boolean = false) {
            paint.color = Color.rgb(40, 38, 64)
            paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(value, 112f, y, paint)
        }
        line("Minescape Daily", 158f, 58f, true)
        line("${record.date} · UTC", 238f, 38f)
        line(if (record.outcome == DailyOutcome.WON) "Won" else "Lost", 402f, 80f, true)
        line(formatTime(record.elapsedMillis), 490f, 64f, true)
        line("First attempt · Intermediate · No-guess", 594f, 32f)
        return bitmap
    }
    fun intent(context: Context, record: DailyRecord): Intent {
        val folder = File(context.cacheDir, "daily-shares").apply { mkdirs() }
        val file = File(folder, "minescape-daily-${record.date}.png")
        val bitmap = card(record)
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text(record))
            // The image type is already known; avoid an unnecessary provider query here.
            clipData = ClipData.newRawUri("Daily result", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
