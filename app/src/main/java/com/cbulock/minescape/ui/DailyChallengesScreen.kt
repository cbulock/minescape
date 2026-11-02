package com.cbulock.minescape.ui

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
import com.cbulock.minescape.R
import com.cbulock.minescape.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun DailyChallengesScreen(data: AppData, today: String, onPlay: (String) -> Unit,
    onResume: () -> Unit, onClose: () -> Unit) {
    val strings = textResources()
    val colors = MaterialTheme.colorScheme
    val records = remember(data.dailyRecords) { data.dailyRecords.toSortedMap(reverseOrder()).values.toList() }
    var visible by rememberSaveable { mutableIntStateOf(10) }
    var archive by rememberSaveable { mutableStateOf(false) }
    val current = data.dailyRecords[today]
    val unfinished = data.dailyGame?.takeIf { !it.finished && it.dailyDate != today }
    if (archive) {
        DailyArchive(today, data.dailyRecords, onPlay = { archive = false; onPlay(it) }, onClose = { archive = false })
        return
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(strings(R.string.daily_challenges), Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onClose) { Text(strings(R.string.done)) }
                    }
                    LazyColumn(Modifier.weight(1f).testTag("daily-list"),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item(key = "today") {
                            Surface(shape = RoundedCornerShape(24.dp), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(strings(R.string.today), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(dailyDateLabel(today), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Text(strings(R.string.intermediate_no_guess), style = MaterialTheme.typography.bodyMedium)
                                    current?.let { Text(strings(R.string.formatted_value, dailyOutcomeLabel(it.outcome, strings), strings.time(it.elapsedMillis)), style = MaterialTheme.typography.titleMedium) }
                                    Button(onClick = { onPlay(today) }, modifier = Modifier.fillMaxWidth()) {
                                        Text(when (current?.outcome) {
                                            null -> strings(R.string.play_today_s_challenge)
                                            DailyOutcome.IN_PROGRESS -> strings(R.string.resume_today_s_challenge)
                                            else -> strings(R.string.practice_today_s_challenge)
                                        })
                                    }
                                    Text(strings(R.string.a_new_shared_puzzle_at_midnight_local_time_your), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        item(key = "archive") {
                            OutlinedButton(onClick = { archive = true }, modifier = Modifier.fillMaxWidth()) { Text("Browse archive") }
                        }
                        unfinished?.let { game ->
                            item(key = "resume") {
                                OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(strings(R.string.pick_up_where_you_left_off), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(dailyDateLabel(game.dailyDate!!), style = MaterialTheme.typography.bodyMedium)
                                        FilledTonalButton(onClick = onResume) { Text(strings(R.string.resume_challenge)) }
                                    }
                                }
                            }
                        }
                        item(key = "history") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(strings(R.string.your_history), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(if (records.isEmpty()) strings(R.string.your_first_attempt_starts_your_story) else
                                    strings(R.string.attempted_won_newest_first, records.size, records.count { it.outcome == DailyOutcome.WON }),
                                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                        if (records.isEmpty()) item(key = "empty") {
                            Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(strings(R.string.a_fresh_challenge_every_day), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(strings(R.string.play_today_s_puzzle_to_start_your_history_finished),
                                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                        items(records.take(visible), key = { it.date }) { record -> DailyHistoryCard(record) { date ->
                            if (data.dailyGame?.dailyDate == date && data.dailyGame.finished == false) onResume() else onPlay(date)
                        } }
                        if (records.size > visible) item(key = "more") {
                            OutlinedButton(onClick = { visible += 10 }, modifier = Modifier.fillMaxWidth()) {
                                Text(strings(R.string.show_more_remaining, records.size - visible))
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
    val strings = textResources()
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
                    Text(dailyOutcomeLabel(record.outcome, strings), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Text(strings.time(record.elapsedMillis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = .6f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = if (largeText) 1 else 2) {
                if (completed) ShareResultButton(record, modifier = if (largeText) Modifier.fillMaxWidth() else Modifier, outlined = true)
                TextButton(onClick = { onPlay(record.date) }, modifier = if (largeText) Modifier.fillMaxWidth() else Modifier) {
                    Text(if (record.outcome == DailyOutcome.IN_PROGRESS) strings(R.string.resume) else strings(R.string.practice))
                }
            }
        }
    }
}

@Composable
private fun dailyDateLabel(date: String): String {
    val strings = textResources()
    val locale = strings.locale
    return remember(date, locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(LocalDate.parse(date)) }
}

private fun dailyOutcomeLabel(outcome: DailyOutcome, strings: TextResources) = when (outcome) {
    DailyOutcome.WON -> strings(R.string.won)
    DailyOutcome.LOST -> strings(R.string.lost)
    DailyOutcome.IN_PROGRESS -> strings(R.string.daily_outcome_in_progress)
    DailyOutcome.ABANDONED -> strings(R.string.abandoned)
}
