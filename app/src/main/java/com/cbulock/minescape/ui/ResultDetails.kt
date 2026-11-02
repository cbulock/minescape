package com.cbulock.minescape.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cbulock.minescape.R
import com.cbulock.minescape.data.DailyRecord
import com.cbulock.minescape.data.GameResult
import com.cbulock.minescape.game.*
import kotlin.math.cos
import kotlin.math.sin

internal data class ResultPresentation(val open: Boolean, val show: () -> Unit, val dismiss: () -> Unit)

/** Kept above page navigation so dismissing a result survives a trip to New game or Settings. */
@Composable
internal fun rememberResultPresentation(game: Game, enabled: Boolean): ResultPresentation {
    var observedStatus by rememberSaveable(game.id) { mutableStateOf(game.status) }
    var open by rememberSaveable(game.id) { mutableStateOf(game.finished) }
    LaunchedEffect(game.id, game.status, enabled, open) {
        if (!game.finished) {
            observedStatus = game.status
            open = false
        } else if (enabled && !open && observedStatus != game.status) {
            // Leave the board visible for its result effect; tween also honors reduced motion.
            Animatable(0f).animateTo(1f, tween(if (game.status == GameStatus.WON) 2800 else 950))
            observedStatus = game.status
            open = true
        }
    }
    return ResultPresentation(open,
        show = { observedStatus = game.status; open = true },
        dismiss = { observedStatus = game.status; open = false })
}

@Composable
internal fun ResultDetailsDialog(
    game: Game,
    result: GameResult?,
    official: DailyRecord?,
    onClose: () -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
    onReplay: () -> Unit,
    onRetry: () -> Unit,
    onUndo: () -> Unit,
) {
    val strings = textResources()
    val won = game.status == GameStatus.WON
    val colors = MaterialTheme.colorScheme
    val accent = if (won) colors.secondary else colors.tertiary
    val container = if (won) colors.secondaryContainer else colors.tertiaryContainer
    val onContainer = if (won) colors.onSecondaryContainer else colors.onTertiaryContainer
    val safeSquares = game.config.size - game.config.mines
    val progress = game.cleared.toFloat() / safeSquares
    val largeText = LocalDensity.current.fontScale > 1.3f
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.safeDrawingPadding().padding(16.dp).widthIn(max = 440.dp).fillMaxWidth().testTag("game-result"),
            shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = strings(R.string.close_result_and_view_board) }) { Text(strings(R.string.view_board)) }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = container, contentColor = onContainer, shape = RoundedCornerShape(22.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!largeText) ResultEmblem(won, accent)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (won) strings(R.string.you_won) else strings(R.string.game_over), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                                Text(if (won) strings(R.string.every_safe_square_found) else strings(R.string.a_mine_got_this_one), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResultBadge(strings.difficulty(game.config.difficulty))
                        ResultBadge(if (game.generation == GenerationMode.NO_GUESS) strings(R.string.no_guess) else strings(R.string.classic))
                        if (game.mode != PlayMode.NORMAL) ResultBadge(if (game.mode == PlayMode.PRACTICE) strings(R.string.practice) else strings(R.string.daily_2))
                    }
                    Text(strings.quantity(R.plurals.result_board_summary, game.config.mines, game.config.columns, game.config.rows, game.config.mines) +
                        (game.dailyDate?.let { strings(R.string.daily_3, it) } ?: ""), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    if (largeText) {
                        ResultStat(strings(R.string.time), strings.time(game.elapsedMillis), Modifier.fillMaxWidth())
                        ResultStat(strings(R.string.safe_squares), strings(R.string.formatted_value_3, game.cleared, safeSquares), Modifier.fillMaxWidth())
                    } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ResultStat(strings(R.string.time), strings.time(game.elapsedMillis), Modifier.weight(1f))
                        ResultStat(strings(R.string.safe_squares), strings(R.string.formatted_value_3, game.cleared, safeSquares), Modifier.weight(1f))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings(R.string.cleared, (progress * 100).toInt()), style = MaterialTheme.typography.labelMedium, color = accent)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = accent, trackColor = container)
                    }
                    if (result?.newBest == true || result?.previousBestMillis != null) {
                        Surface(color = colors.primaryContainer, contentColor = colors.onPrimaryContainer, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (result.newBest) Text(strings(R.string.new_personal_best), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(result.previousBestMillis?.let { strings(R.string.previous_best, strings.time(it)) } ?: strings(R.string.your_first_recorded_win_at_this_difficulty_and_mode), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (game.mode == PlayMode.PRACTICE) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ResultBadge(strings.quantity(R.plurals.hints_used, game.hintsUsed, game.hintsUsed))
                            ResultBadge(strings.quantity(R.plurals.undos_used, game.undosUsed, game.undosUsed))
                            ResultBadge(strings.quantity(R.plurals.mines_hit, game.minesHit, game.minesHit))
                        }
                        Text(strings(R.string.practice_does_not_affect_records), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReplay, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                            Text(if (game.dailyDate != null) strings(R.string.practice_again) else strings(R.string.play_another_game, strings.difficulty(game.config.difficulty)))
                        }
                        if (game.mode == PlayMode.PRACTICE) {
                            OutlinedButton(onClick = onUndo, enabled = game.undo.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text(strings(R.string.undo)) }
                        }
                        if (game.dailyDate == null) {
                            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                                Text(strings(R.string.retry_same_board_in_practice))
                            }
                            Text(if (game.openingCells.isEmpty()) strings(R.string.same_mines_a_safe_opening_clock_and_markers_reset)
                                else strings(R.string.same_mines_and_opening_clock_and_markers_reset_records),
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FinishedGameActions(official, onNew)
            }
        }
    }
}

/** Keep the next steps available while result details or the board are being inspected. */
@Composable
internal fun FinishedGameActions(official: DailyRecord?, onNew: () -> Unit, modifier: Modifier = Modifier, horizontal: Boolean = false) {
    val strings = textResources()
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (horizontal) 2 else 1) {
        if (official != null) {
            ShareResultButton(official, Modifier.weight(1f).heightIn(min = 48.dp), prominent = true)
            OutlinedButton(onClick = onNew, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                Text(strings(R.string.new_game))
            }
        } else {
            Button(onClick = onNew, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                Text(strings(R.string.new_game))
            }
        }
    }
}

@Composable
private fun ResultBadge(label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(8.dp)) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ResultStat(label: String, value: String, modifier: Modifier) {
    Surface(modifier.semantics(mergeDescendants = true) {}, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ResultEmblem(won: Boolean, color: Color) {
    Canvas(Modifier.size(52.dp)) {
        val c = center
        val radius = size.minDimension * .28f
        drawCircle(color.copy(alpha = .12f))
        if (won) {
            val star = Path()
            repeat(10) { i ->
                val angle = -Math.PI / 2 + i * Math.PI / 5
                val r = if (i % 2 == 0) radius * 1.25f else radius * .58f
                val x = c.x + cos(angle).toFloat() * r
                val y = c.y + sin(angle).toFloat() * r
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            drawPath(star, color)
        } else {
            repeat(8) { i ->
                val angle = i * Math.PI / 4
                val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                drawLine(color, c + direction * radius, c + direction * radius * 1.4f, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            drawCircle(color, radius)
            drawCircle(Color.White.copy(alpha = .8f), radius * .2f, c - Offset(radius * .3f, radius * .3f))
        }
    }
}
