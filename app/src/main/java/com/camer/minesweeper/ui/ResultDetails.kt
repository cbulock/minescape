package com.camer.minesweeper.ui

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
import com.camer.minesweeper.data.DailyRecord
import com.camer.minesweeper.data.GameResult
import com.camer.minesweeper.game.*
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
                    TextButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Close result and view board" }) { Text("View board") }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = container, contentColor = onContainer, shape = RoundedCornerShape(22.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!largeText) ResultEmblem(won, accent)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (won) "You won" else "Game over", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                                Text(if (won) "Every safe square, found." else "A mine got this one.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResultBadge(game.config.difficulty.title)
                        ResultBadge(if (game.generation == GenerationMode.NO_GUESS) "No-guess" else "Classic")
                        if (game.mode != PlayMode.NORMAL) ResultBadge(if (game.mode == PlayMode.PRACTICE) "Practice" else "Daily")
                    }
                    Text("${game.config.columns} × ${game.config.rows} board · ${game.config.mines} mines" +
                        (game.dailyDate?.let { "\nDaily · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    if (largeText) {
                        ResultStat("Time", formatTime(game.elapsedMillis), Modifier.fillMaxWidth())
                        ResultStat("Safe squares", "${game.cleared} / $safeSquares", Modifier.fillMaxWidth())
                    } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ResultStat("Time", formatTime(game.elapsedMillis), Modifier.weight(1f))
                        ResultStat("Safe squares", "${game.cleared} / $safeSquares", Modifier.weight(1f))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${(progress * 100).toInt()}% cleared", style = MaterialTheme.typography.labelMedium, color = accent)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = accent, trackColor = container)
                    }
                    if (result?.newBest == true || result?.previousBestMillis != null) {
                        Surface(color = colors.primaryContainer, contentColor = colors.onPrimaryContainer, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (result.newBest) Text("New personal best", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(result.previousBestMillis?.let { "Previous best ${formatTime(it)}" } ?: "Your first recorded win at this difficulty and mode.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (game.mode == PlayMode.PRACTICE) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ResultBadge("${game.hintsUsed} ${if (game.hintsUsed == 1) "hint" else "hints"}")
                            ResultBadge("${game.undosUsed} ${if (game.undosUsed == 1) "undo" else "undos"}")
                            ResultBadge("${game.minesHit} ${if (game.minesHit == 1) "mine hit" else "mines hit"}")
                        }
                        Text("Practice does not affect records.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReplay, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                            Text(if (game.dailyDate != null) "Practice again" else "Play again")
                        }
                        if (game.mode == PlayMode.PRACTICE) {
                            OutlinedButton(onClick = onUndo, enabled = game.undo.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text("Undo") }
                        }
                        if (game.dailyDate == null) {
                            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                                Text("Retry same board in practice")
                            }
                            Text(if (game.openingCells.isEmpty()) "Same mines, a safe opening. Clock and markers reset; records stay unchanged."
                                else "Same mines and opening. Clock and markers reset; records stay unchanged.",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("New game")
                }
                if (official != null) {
                    Spacer(Modifier.height(12.dp))
                    ShareResultButton(official, Modifier.fillMaxWidth().heightIn(min = 48.dp), outlined = true)
                }
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
