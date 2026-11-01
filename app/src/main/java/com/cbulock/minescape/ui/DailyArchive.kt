package com.cbulock.minescape.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cbulock.minescape.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@Composable
internal fun DailyArchive(today: String, records: Map<String, DailyRecord>, onPlay: (String) -> Unit, onClose: () -> Unit) {
    val current = LocalDate.parse(today)
    val firstDate = minOf(LocalDate.of(2026, 1, 1), current)
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(current).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(today) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val offset = Math.floorMod(month.atDay(1).dayOfWeek.value - firstDay.value, 7)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().widthIn(max = 640.dp).fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily archive", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onClose) { Text("Done") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Missed a day? Past puzzles are practice and leave your official results unchanged.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { monthText = month.minusMonths(1).toString() }, enabled = month > YearMonth.from(firstDate),
                            modifier = Modifier.semantics { contentDescription = "Previous month" }) { Text("‹") }
                        Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = month < YearMonth.from(current),
                            modifier = Modifier.semantics { contentDescription = "Next month" }) { Text("›") }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(firstDay.plus(column.toLong()).getDisplayName(TextStyle.NARROW_STANDALONE, locale), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    repeat((offset + month.lengthOfMonth() + 6) / 7) { row ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { column ->
                                val day = row * 7 + column - offset + 1
                                if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f)) else {
                                    val date = month.atDay(day)
                                    val record = records[date.toString()]
                                    val state = when (record?.outcome) {
                                        DailyOutcome.WON -> "Won"
                                        DailyOutcome.LOST -> "Lost"
                                        DailyOutcome.IN_PROGRESS -> "In progress"
                                        DailyOutcome.ABANDONED -> "Abandoned"
                                        null -> if (date > current) "Not available yet" else "Not attempted"
                                    }
                                    val marker = when (record?.outcome) { DailyOutcome.WON -> "✓"; DailyOutcome.LOST -> "×"; null -> ""; else -> "•" }
                                    TextButton(onClick = { selectedText = date.toString() }, enabled = date in firstDate..current,
                                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("archive-$date")
                                            .semantics { contentDescription = "${date.format(dateFormat)}, $state" },
                                        contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(
                                            containerColor = if (date == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            contentColor = if (date == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(day.toString(), style = MaterialTheme.typography.bodyMedium)
                                            if (marker.isNotEmpty()) Text(marker, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("✓ Won    × Lost    • Attempted", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text(selected.format(dateFormat), style = MaterialTheme.typography.titleMedium)
                    Text(if (selected == current) "Today's first attempt counts. Replays are practice." else "Intermediate · No-guess · Practice")
                    Button(onClick = { onPlay(selectedText) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selected == current) "Open today's puzzle" else "Play as practice")
                    }
                }
            }
        }
    }
}
