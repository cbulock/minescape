package com.camer.minesweeper.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.camer.minesweeper.data.*
import com.camer.minesweeper.game.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun StatisticsScreen(data: AppData, onClose: () -> Unit) {
    var generation by rememberSaveable { mutableStateOf(GenerationMode.CLASSIC) }
    var difficulty by rememberSaveable { mutableStateOf<Difficulty?>(null) }
    var choosingDifficulty by remember { mutableStateOf(false) }
    var showCountingRules by rememberSaveable { mutableStateOf(false) }
    var visibleResults by rememberSaveable(generation, difficulty) { mutableIntStateOf(10) }
    val largeText = LocalDensity.current.fontScale > 1.3f
    val listState = rememberLazyListState()
    val difficulties = Difficulty.entries.filter { generation == GenerationMode.CLASSIC || it != Difficulty.CUSTOM }
    val selected = difficulties.filter { difficulty == null || it == difficulty }
    val statistics = if (generation == GenerationMode.CLASSIC) data.statistics else data.noGuessStatistics
    val wins = selected.sumOf { (statistics[it]?.wins ?: 0).toLong() }
    val losses = selected.sumOf { (statistics[it]?.losses ?: 0).toLong() }
    val results = remember(data.results, generation, difficulty) {
        data.results.asReversed().filter { it.generation == generation && (difficulty == null || it.config.difficulty == difficulty) }
    }
    val modeName = if (generation == GenerationMode.CLASSIC) "Classic" else "No-guess"

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Statistics", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onClose) { Text("Done") }
                    }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("statistics-list"),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item(key = "filters") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (largeText) 1 else 2) {
                                    GenerationMode.entries.forEach { mode ->
                                        FilterChip(selected = generation == mode, modifier = Modifier.weight(1f),
                                            onClick = {
                                                generation = mode
                                                if (mode == GenerationMode.NO_GUESS && difficulty == Difficulty.CUSTOM) difficulty = null
                                            }, label = { Text(if (mode == GenerationMode.CLASSIC) "Classic" else "No-guess") })
                                    }
                                }
                                Box {
                                    OutlinedButton(onClick = { choosingDifficulty = true }, modifier = Modifier.fillMaxWidth()
                                        .semantics { contentDescription = "Difficulty filter: ${difficulty?.title ?: "All difficulties"}" },
                                        shape = RoundedCornerShape(14.dp)) {
                                        Text(difficulty?.title ?: "All difficulties", Modifier.weight(1f))
                                        Text("⌄", Modifier.padding(start = 8.dp))
                                    }
                                    DropdownMenu(expanded = choosingDifficulty, onDismissRequest = { choosingDifficulty = false }) {
                                        (listOf<Difficulty?>(null) + difficulties).forEach { value ->
                                            DropdownMenuItem(text = { Text(value?.title ?: "All difficulties") }, onClick = {
                                                difficulty = value
                                                choosingDifficulty = false
                                            })
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "overview") {
                            StatisticsOverview(wins, losses, "$modeName · ${difficulty?.title ?: "All difficulties"}")
                        }
                        if (wins + losses > 0) {
                            item(key = "records-title") { StatisticsHeading(if (difficulty == null) "By difficulty" else "Your records") }
                            items(selected, key = { "difficulty-${it.name}" }) { value ->
                                DifficultyStatistics(value, statistics[value] ?: Statistics())
                            }
                        }
                        item(key = "history-title") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                StatisticsHeading("Recent results")
                                Text("Newest first · From your last 100 games", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (results.isEmpty()) {
                            item(key = "history-empty") {
                                Text(if (wins + losses == 0L) "Finished games will appear here." else "No recent games match this filter. Your all-time records are shown above.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(results.take(visibleResults), key = { "result-${it.id}" }) { result -> RecentResult(result) }
                        if (results.size > visibleResults) {
                            item(key = "more") {
                                OutlinedButton(onClick = { visibleResults += 10 }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Show more results (${results.size - visibleResults} remaining)")
                                }
                            }
                        }
                        item(key = "rules") {
                            Column {
                                TextButton(onClick = { showCountingRules = !showCountingRules }) {
                                    Text(if (showCountingRules) "Hide counting details" else "What counts toward statistics?")
                                }
                                if (showCountingRules) Text(
                                    "Only finished standard games count. Practice is excluded; daily challenges have their own history. Unfinished boards do not count as losses.\n\nCustom boards share totals, but have no combined best time. Streaks count consecutive wins within each difficulty and mode; older games from before streak tracking are not included.",
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsOverview(wins: Long, losses: Long, scope: String) {
    val games = wins + losses
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(scope, style = MaterialTheme.typography.labelLarge)
            if (games == 0L) {
                Text("A fresh start", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Finish a game in this mode and difficulty to start building your records.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column {
                    Text("%.1f%%".format(wins * 100.0 / games), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text("All-time win rate", style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(progress = { (wins.toFloat() / games).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp), trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .12f))
                StatisticsMetrics(listOf("Played" to "$games", "Won" to "$wins", "Lost" to "$losses"))
            }
        }
    }
}

@Composable
private fun DifficultyStatistics(difficulty: Difficulty, stats: Statistics) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(difficulty.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (stats.wins + stats.losses == 0) "No finished games yet" else
                    "${stats.wins} won · ${stats.losses} lost · ${"%.1f%%".format(stats.winPercent)} wins",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (stats.wins + stats.losses > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                val metrics = buildList {
                    if (difficulty != Difficulty.CUSTOM) add("Best time" to (stats.bestMillis?.let(::formatTime) ?: "—"))
                    add("Current streak" to "${stats.currentStreak}")
                    add("Best streak" to "${stats.bestStreak}")
                }
                StatisticsMetrics(metrics)
                if (difficulty == Difficulty.CUSTOM) Text("All custom board sizes combined", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatisticsMetrics(metrics: List<Pair<String, String>>) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            metrics.forEach { (label, value) -> StatisticsMetric(label, value, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            metrics.forEach { (label, value) -> StatisticsMetric(label, value, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun StatisticsMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatisticsHeading(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun RecentResult(result: GameResult) {
    val colors = MaterialTheme.colorScheme
    val date = remember(result.finishedAt) {
        runCatching { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.parse(result.finishedAt).atZone(ZoneId.systemDefault())) }
            .getOrDefault(result.finishedAt.take(10))
    }
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                Text(if (result.won) "Won" else "Lost", color = if (result.won) colors.secondary else colors.tertiary,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(result.config.difficulty.title, style = MaterialTheme.typography.labelLarge)
                Text(formatTime(result.elapsedMillis), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(date, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (result.newBest) Text("Personal best when played", style = MaterialTheme.typography.labelMedium, color = colors.primary)
        }
    }
}
