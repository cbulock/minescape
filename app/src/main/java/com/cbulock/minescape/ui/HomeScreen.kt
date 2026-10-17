package com.cbulock.minescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cbulock.minescape.data.Preferences
import com.cbulock.minescape.game.*

@Composable
internal fun HomeScreen(
    onChoose: (BoardConfig) -> Unit,
    currentGame: Game?,
    onResume: () -> Unit,
    preferences: Preferences,
    onPreferences: (Preferences) -> Unit,
    onDaily: () -> Unit,
    onLearn: () -> Unit,
    onStatistics: () -> Unit,
) {
    var custom by rememberSaveable { mutableStateOf(false) }
    var options by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val mode = if (preferences.generation == GenerationMode.NO_GUESS) "No-guess" else "Classic"
    val modeSummary = "$mode · ${if (preferences.practice) "Practice" else "Standard"}"
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (currentGame != null) {
                Card(onClick = onResume, colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onSurface), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (currentGame.finished) "View last game" else "Back to current game", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${if (currentGame.dailyDate != null) "Daily" else currentGame.config.difficulty.title} · ${formatTime(currentGame.elapsedMillis)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("→", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose your board", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Surface(
                    onClick = { options = true }, shape = RoundedCornerShape(12.dp), color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Game options" },
                ) {
                    FlowRow(
                        Modifier.heightIn(min = 48.dp).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(modeSummary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Text("Game options", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                }
                Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
                    Column {
                        listOf(Difficulty.BEGINNER, Difficulty.INTERMEDIATE, Difficulty.EXPERT).forEachIndexed { index, difficulty ->
                            val config = BoardConfig.preset(difficulty)
                            val accent = when (index) { 0 -> colors.secondary; 1 -> colors.primary; else -> colors.tertiary }
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Start ${difficulty.title} game") { onChoose(config) }
                                    .heightIn(min = 80.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (LocalDensity.current.fontScale <= 1.3f) {
                                    BoardGlyph(accent, index + 2)
                                    Spacer(Modifier.width(16.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(difficulty.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${config.columns} × ${config.rows} · ${config.mines} mines", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                }
                                Text("›", Modifier.padding(start = 8.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
                            }
                            if (index < 2) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant.copy(alpha = .55f))
                        }
                    }
                }
                TextButton(onClick = { custom = !custom }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (custom) "Hide custom board" else "Make a custom board")
                }
                if (custom) {
                    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Custom board", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Classic boards · ${if (preferences.practice) "Practice with hints and undo" else "Standard play"}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            CustomBoard(onChoose, preferences.lastCustom)
                        }
                    }
                }
            }
            Card(onClick = onDaily, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onSurface)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Daily challenge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Intermediate · No-guess", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("→", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onLearn) { Text("Learn to play") }
                TextButton(onClick = onStatistics) { Text("Statistics") }
            }
        }
    }
    if (options) {
        AlertDialog(
            onDismissRequest = { options = false },
            title = { Text("Game options") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    HomeOption("Practice", "Hints and undo · records unaffected", "Practice mode", preferences.practice) {
                        onPreferences(preferences.copy(practice = it))
                    }
                    HomeOption("No-guess presets", "Every move can be deduced. Applies to Beginner, Intermediate, and Expert.", "No-guess presets", preferences.generation == GenerationMode.NO_GUESS) {
                        onPreferences(preferences.copy(generation = if (it) GenerationMode.NO_GUESS else GenerationMode.CLASSIC))
                    }
                    Text("These options are saved for your next games. Daily challenges use their own rules.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { options = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun HomeOption(title: String, description: String, label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.titleSmall)
            Switch(checked, onChecked, modifier = Modifier.semantics { contentDescription = label })
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BoardGlyph(color: Color, columns: Int) {
    Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(14.dp)) {
        Canvas(Modifier.size(44.dp).padding(10.dp)) {
            val gap = 2.dp.toPx()
            val tile = (size.width - gap * (columns - 1)) / columns
            repeat(columns * columns) { index ->
                drawRoundRect(color.copy(alpha = if (index % 3 == 0) .45f else .9f),
                    topLeft = Offset((index % columns) * (tile + gap), (index / columns) * (tile + gap)),
                    size = Size(tile, tile), cornerRadius = CornerRadius(1.dp.toPx()))
            }
        }
    }
}
