package com.cbulock.minescape.ui

import android.content.ClipData
import android.content.Context
import android.content.res.Resources
import com.cbulock.minescape.R
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.cbulock.minescape.data.DailyOutcome
import com.cbulock.minescape.data.DailyRecord
import com.cbulock.minescape.game.ChallengeLink
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DailyShare {
    private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.cbulock.minescape"

    // Accept only the official record: no layout, coordinates, or practice assistance can leak.
    private fun date(record: DailyRecord, locale: Locale): String =
        LocalDate.parse(record.date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    fun text(record: DailyRecord, resources: Resources): String {
        val strings = TextResources(resources)
        require(record.outcome == DailyOutcome.WON || record.outcome == DailyOutcome.LOST)
        return strings(R.string.share_text, strings(R.string.app_name), date(record, strings.locale),
            strings(if (record.outcome == DailyOutcome.WON) R.string.won else R.string.lost),
            strings.time(record.elapsedMillis), ChallengeLink.daily(record.date), PLAY_STORE_URL)
    }
    fun card(record: DailyRecord, resources: Resources): Bitmap {
        val strings = TextResources(resources)
        text(record, resources)
        val bitmap = createBitmap(1080, 720)
        val canvas = Canvas(bitmap)
        val ink = Color.rgb(40, 38, 64)
        val violet = Color.rgb(101, 84, 217)
        val muted = Color.rgb(97, 96, 117)
        val mint = Color.rgb(184, 233, 215)
        val won = record.outcome == DailyOutcome.WON
        val accent = if (won) mint else Color.rgb(255, 218, 212)
        canvas.drawColor(Color.rgb(234, 230, 255))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun rect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
            paint.color = color
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        }
        fun line(value: String, x: Float, y: Float, size: Float, color: Int = ink,
                 bold: Boolean = false, maxWidth: Float = 880f) {
            paint.color = color
            paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            if (paint.measureText(value) > maxWidth) paint.textSize *= maxWidth / paint.measureText(value)
            canvas.drawText(value, x, y, paint)
        }
        rect(32f, 40f, 1048f, 688f, 40f, Color.rgb(218, 212, 244))
        rect(32f, 32f, 1048f, 680f, 40f, Color.rgb(252, 251, 255))
        line(strings(R.string.app_name), 80f, 123f, 44f, bold = true)
        line(strings(R.string.share_daily_challenge), 80f, 195f, 24f, violet, true)
        line(date(record, strings.locale), 80f, 240f, 32f, muted)

        // Keep the outcome legible even when the receiving app omits the caption.
        rect(80f, 278f, 1000f, 380f, 24f,
            if (won) Color.rgb(28, 108, 81) else Color.rgb(167, 45, 60))
        paint.color = Color.WHITE
        paint.strokeWidth = 8f
        paint.strokeCap = Paint.Cap.ROUND
        if (won) {
            canvas.drawLine(114f, 326f, 132f, 344f, paint)
            canvas.drawLine(132f, 344f, 162f, 309f, paint)
        } else {
            canvas.drawLine(120f, 310f, 154f, 344f, paint)
            canvas.drawLine(154f, 310f, 120f, 344f, paint)
        }
        line(strings(if (won) R.string.you_won else R.string.you_lost), 196f, 351f, 64f,
            Color.WHITE, bold = true, maxWidth = 772f)
        line(strings.time(record.elapsedMillis), 72f, 500f, 104f, bold = true, maxWidth = 550f)
        line(strings(R.string.first_attempt), 80f, 544f, 28f, muted, maxWidth = 550f)

        // A fixed decorative motif, never a preview of the daily layout or player moves.
        canvas.save()
        canvas.translate(405f, 264f)
        canvas.scale(0.5f, 0.5f)
        canvas.rotate(-8f, 827f, 400f)
        for (row in 0..2) for (col in 0..2) {
            val x = 678f + col * 102f
            val y = 252f + row * 102f
            val center = row == 1 && col == 1
            rect(x, y + 5f, x + 90f, y + 95f, 20f, Color.rgb(222, 217, 241))
            rect(x, y, x + 90f, y + 90f, 20f,
                if (center) violet else if ((row + col) % 3 == 0) accent else Color.rgb(234, 230, 255))
            if (center) {
                paint.color = Color.WHITE
                paint.strokeWidth = 5f
                canvas.drawLine(x + 32f, y + 24f, x + 32f, y + 68f, paint)
                canvas.drawLine(x + 23f, y + 69f, x + 48f, y + 69f, paint)
                canvas.drawPath(Path().apply {
                    moveTo(x + 34f, y + 23f); lineTo(x + 65f, y + 34f)
                    lineTo(x + 34f, y + 47f); close()
                }, paint)
            }
        }
        canvas.restore()
        rect(80f, 572f, 1000f, 574f, 1f, Color.rgb(233, 230, 243))
        line(strings(R.string.difficulty_intermediate), 80f, 632f, 28f, bold = true, maxWidth = 220f)
        line(strings(R.string.share_board), 330f, 632f, 26f, muted, maxWidth = 450f)
        rect(812f, 596f, 1000f, 648f, 26f, Color.rgb(234, 230, 255))
        line(strings(R.string.no_guess), 839f, 631f, 26f, violet, true, maxWidth = 134f)
        return bitmap
    }
    fun intent(context: Context, record: DailyRecord): Intent {
        val folder = File(context.cacheDir, "daily-shares").apply { mkdirs() }
        // A new URI prevents receiving apps from reusing a previous card thumbnail.
        val file = File.createTempFile("minescape-daily-${record.date}-", ".png", folder)
        val bitmap = card(record, context.resources)
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text(record, context.resources))
            // The image type is already known; avoid an unnecessary provider query here.
            clipData = ClipData.newRawUri(context.getString(R.string.daily_result), uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
