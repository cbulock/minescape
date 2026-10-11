package com.camer.minesweeper.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.camer.minesweeper.data.*
import com.camer.minesweeper.game.formatTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun DailyChallengesScreen(data: AppData, today: String, onPlay: (String) -> Unit,
    onResume: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val records = remember(data.dailyRecords) { data.dailyRecords.toSortedMap(reverseOrder()).values.toList() }
    var visible by rememberSaveable { mutableIntStateOf(10) }
    val current = data.dailyRecords[today]
    val unfinished = data.dailyGame?.takeIf { !it.finished && it.dailyDate != today }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Daily challenges", Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onClose) { Text("Done") }
                    }
                    LazyColumn(Modifier.weight(1f).testTag("daily-list"),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item(key = "today") {
                            Surface(shape = RoundedCornerShape(24.dp), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("TODAY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(dailyDateLabel(today), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Text("Intermediate · No-guess", style = MaterialTheme.typography.bodyMedium)
                                    current?.let { Text("${dailyOutcomeLabel(it.outcome)} · ${formatTime(it.elapsedMillis)}", style = MaterialTheme.typography.titleMedium) }
                                    Button(onClick = { onPlay(today) }, modifier = Modifier.fillMaxWidth()) {
                                        Text(when (current?.outcome) {
                                            null -> "Play today's challenge"
                                            DailyOutcome.IN_PROGRESS -> "Resume today's challenge"
                                            else -> "Practice today's challenge"
                                        })
                                    }
                                    Text("A new shared puzzle at midnight local time. Your first attempt counts; replays are practice.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        unfinished?.let { game ->
                            item(key = "resume") {
                                OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Pick up where you left off", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(dailyDateLabel(game.dailyDate!!), style = MaterialTheme.typography.bodyMedium)
                                        FilledTonalButton(onClick = onResume) { Text("Resume challenge") }
                                    }
                                }
                            }
                        }
                        item(key = "history") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Your history", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(if (records.isEmpty()) "Your first attempt starts your story." else
                                    "${records.size} attempted · ${records.count { it.outcome == DailyOutcome.WON }} won · Newest first",
                                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                        if (records.isEmpty()) item(key = "empty") {
                            Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("A fresh challenge every day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Play today's puzzle to start your history. Finished results can be shared without revealing the board.",
                                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                        items(records.take(visible), key = { it.date }) { record -> DailyHistoryCard(record, onPlay) }
                        if (records.size > visible) item(key = "more") {
                            OutlinedButton(onClick = { visible += 10 }, modifier = Modifier.fillMaxWidth()) {
                                Text("Show more (${records.size - visible} remaining)")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyHistoryCard(record: DailyRecord, onPlay: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale > 1.3f
    val completed = record.outcome == DailyOutcome.WON || record.outcome == DailyOutcome.LOST
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(dailyDateLabel(record.date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = when (record.outcome) {
                    DailyOutcome.WON -> colors.secondaryContainer
                    DailyOutcome.LOST -> colors.tertiaryContainer
                    else -> colors.surfaceContainerHighest
                }) {
                    Text(dailyOutcomeLabel(record.outcome), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Text(formatTime(record.elapsedMillis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = .6f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = if (largeText) 1 else 2) {
                if (completed) ShareResultButton(record, modifier = if (largeText) Modifier.fillMaxWidth() else Modifier, outlined = true)
                TextButton(onClick = { onPlay(record.date) }, modifier = if (largeText) Modifier.fillMaxWidth() else Modifier) {
                    Text(if (record.outcome == DailyOutcome.IN_PROGRESS) "Resume" else "Practice")
                }
            }
        }
    }
}

@Composable
private fun dailyDateLabel(date: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(LocalDate.parse(date)) }
}

private fun dailyOutcomeLabel(outcome: DailyOutcome) = when (outcome) {
    DailyOutcome.WON -> "Won"
    DailyOutcome.LOST -> "Lost"
    DailyOutcome.IN_PROGRESS -> "In progress"
    DailyOutcome.ABANDONED -> "Abandoned"
}
