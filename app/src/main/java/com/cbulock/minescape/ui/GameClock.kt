package com.cbulock.minescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cbulock.minescape.R

@Composable
internal fun GameClock(elapsedMillis: Long, modifier: Modifier = Modifier) {
    val strings = textResources()
    val time = strings.time(elapsedMillis)
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = strings(R.string.time_value, time) },
        shape = RoundedCornerShape(14.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        BoxWithConstraints(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // Keep the full time readable when accessibility text or long games need the space.
            val showIcon = LocalDensity.current.fontScale <= 1.3f && maxWidth >= 80.dp && time.length <= 5
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showIcon) Canvas(Modifier.size(16.dp)) {
                    val stroke = 1.5.dp.toPx()
                    drawCircle(colors.onPrimaryContainer, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
                    drawLine(colors.onPrimaryContainer, center, Offset(center.x, size.height * .26f), stroke, StrokeCap.Round)
                    drawLine(colors.onPrimaryContainer, center, Offset(size.width * .68f, size.height * .59f), stroke, StrokeCap.Round)
                }
                Text(
                    time,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp),
                )
            }
        }
    }
}
