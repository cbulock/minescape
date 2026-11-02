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
import com.cbulock.minescape.R
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
    val strings = textResources()
    var custom by rememberSaveable { mutableStateOf(false) }
    var options by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val mode = if (preferences.generation == GenerationMode.NO_GUESS) strings(R.string.no_guess) else strings(R.string.classic)
    val modeSummary = strings(R.string.formatted_value, mode, if (preferences.practice) strings(R.string.practice) else strings(R.string.standard))
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
                            Text(if (currentGame.finished) strings(R.string.view_last_game) else strings(R.string.back_to_current_game), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(strings(R.string.formatted_value, if (currentGame.dailyDate != null) strings(R.string.daily_2) else strings.difficulty(currentGame.config.difficulty), strings.time(currentGame.elapsedMillis)), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("→", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings(R.string.choose_your_board), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Surface(
                    onClick = { options = true }, shape = RoundedCornerShape(12.dp), color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = strings(R.string.game_options) },
                ) {
                    FlowRow(
                        Modifier.heightIn(min = 48.dp).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(modeSummary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Text(strings(R.string.game_options), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                }
                Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
                    Column {
                        listOf(Difficulty.BEGINNER, Difficulty.INTERMEDIATE, Difficulty.EXPERT).forEachIndexed { index, difficulty ->
                            val config = BoardConfig.preset(difficulty)
                            val accent = when (index) { 0 -> colors.secondary; 1 -> colors.primary; else -> colors.tertiary }
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = strings(R.string.start_game, strings.difficulty(difficulty))) { onChoose(config) }
                                    .heightIn(min = 80.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (LocalDensity.current.fontScale <= 1.3f) {
                                    BoardGlyph(accent, index + 2)
                                    Spacer(Modifier.width(16.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(strings.difficulty(difficulty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(strings.quantity(R.plurals.board_summary, config.mines, config.columns, config.rows, config.mines), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                }
                                Text("›", Modifier.padding(start = 8.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
                            }
                            if (index < 2) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant.copy(alpha = .55f))
                        }
                    }
                }
                TextButton(onClick = { custom = !custom }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (custom) strings(R.string.hide_custom_board) else strings(R.string.make_a_custom_board))
                }
                if (custom) {
                    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(strings(R.string.custom_board), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(strings(R.string.classic_boards, if (preferences.practice) strings(R.string.practice_with_hints_and_undo) else strings(R.string.standard_play)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            CustomBoard(onChoose, preferences.lastCustom)
                        }
                    }
                }
            }
            Card(onClick = onDaily, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onSurface)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings(R.string.daily_challenge), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(strings(R.string.intermediate_no_guess), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("→", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onLearn) { Text(strings(R.string.learn_to_play)) }
                TextButton(onClick = onStatistics) { Text(strings(R.string.statistics)) }
            }
        }
    }
    if (options) {
        AlertDialog(
            onDismissRequest = { options = false },
            title = { Text(strings(R.string.game_options)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    HomeOption(strings(R.string.practice), strings(R.string.hints_and_undo_records_unaffected), strings(R.string.practice_mode), preferences.practice) {
                        onPreferences(preferences.copy(practice = it))
                    }
                    HomeOption(strings(R.string.no_guess_presets), strings(R.string.every_move_can_be_deduced_applies_to_beginner_intermediate), strings(R.string.no_guess_presets), preferences.generation == GenerationMode.NO_GUESS) {
                        onPreferences(preferences.copy(generation = if (it) GenerationMode.NO_GUESS else GenerationMode.CLASSIC))
                    }
                    Text(strings(R.string.these_options_are_saved_for_your_next_games_daily), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { options = false }) { Text(strings(R.string.done)) } },
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
