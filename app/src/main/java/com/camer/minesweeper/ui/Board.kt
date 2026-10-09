package com.camer.minesweeper.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.camer.minesweeper.game.*
import kotlin.math.roundToInt

@Composable
fun Board(game: Game, onCell: (CellIntent) -> Unit, onViewport: (Viewport) -> Unit, modifier: Modifier = Modifier, controls: @Composable RowScope.() -> Unit = {}, inputEnabled: Boolean = true, hintIndex: Int? = null,
    holdMillis: Long? = null, tapToChord: Boolean = true, questionMarks: Boolean = false, tagPrefix: String = "") {
    var viewport by remember(game.id) { mutableStateOf(game.viewport) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.red < .3f
    val geometry = BoardGeometry(game.config, area.width / density, area.height / density, viewport)
    val currentGeometry by rememberUpdatedState(geometry)
    // Cell updates must reach the running gesture handler without cancelling a hold.
    val currentGame by rememberUpdatedState(game)
    val currentCell by rememberUpdatedState(onCell)
    val currentSave by rememberUpdatedState(onViewport)
    val currentDensity by rememberUpdatedState(density)
    var flagPreview by remember(game.id, questionMarks) { mutableStateOf<Pair<Int, Cell>?>(null) }
    LaunchedEffect(game.cells, inputEnabled, holdMillis, tapToChord) {
        val preview = flagPreview
        if (!inputEnabled || game.finished || (preview != null &&
            (game.cells[preview.first].let { it.flagged == preview.second.flagged && it.questioned == preview.second.questioned } || game.cells[preview.first].revealed))) flagPreview = null
    }
    var pendingTarget by remember(game.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(game.viewport) { viewport = game.viewport }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.BOLD) } }
    val numberColors = if (dark) listOf(Color.Transparent, Color(0xFFB3A7FF), Color(0xFF82DDB4), Color(0xFFFFAA9B), Color(0xFF8DCBFF), Color(0xFFFFD67B), Color(0xFF88DBE4), Color(0xFFF7AFE0), Color(0xFFDFD6EF))
        else listOf(Color.Transparent, Color(0xFF6048C5), Color(0xFF167158), Color(0xFFB5483C), Color(0xFF286EA6), Color(0xFF8F6012), Color(0xFF1D7581), Color(0xFF9C3E80), Ink)

    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant.copy(alpha = .55f))
            .padding(8.dp).onSizeChanged { area = it }.testTag("${tagPrefix}board")
            .pointerInput(game.id, game.flagMode, game.finished, inputEnabled, area, density, game.viewport, holdMillis, tapToChord, questionMarks) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = true)
                    if (!inputEnabled || game.finished) return@awaitEachGesture
                    val anchorGeometry = currentGeometry
                    val anchor = anchorGeometry.hit(first.position.x / currentDensity, first.position.y / currentDensity)
                    if (anchorGeometry.cell < 32f && anchor != null) {
                        viewport = anchorGeometry.transform(32f / anchorGeometry.cell, 0f, 0f,
                            first.position.x / currentDensity, first.position.y / currentDensity)
                        first.consume()
                        do { val remaining = awaitPointerEvent(); remaining.changes.forEach { it.consume() } }
                        while (remaining.changes.any { it.pressed })
                        currentSave(viewport)
                        return@awaitEachGesture
                    }
                    val timeout = holdMillis ?: viewConfiguration.longPressTimeoutMillis
                    val press = PressGesture(anchor, currentGame.cells.getOrNull(anchor ?: -1)?.revealed == true,
                        game.flagMode, timeout, viewConfiguration.touchSlop, tapToChord)
                    var lastEventTime = first.uptimeMillis
                    var transformed = false
                    fun emit(action: CellAction?) {
                        if (action != null && anchor != null) {
                            if (action == CellAction.TOGGLE_FLAG) {
                                val cell = flagPreview?.takeIf { it.first == anchor }?.second ?: currentGame.cells[anchor]
                                flagPreview = anchor to when {
                                    cell.flagged -> cell.copy(flagged = false, questioned = questionMarks)
                                    cell.questioned -> cell.copy(questioned = false)
                                    else -> cell.copy(flagged = true)
                                }
                            }
                            currentCell(CellIntent(game.id, anchor, action, game.flagMode, fromTap = action == CellAction.CHORD))
                        }
                    }
                    try {
                        pendingTarget = press.pendingTarget
                        while (true) {
                            val event = if (press.state == PressGesture.State.PENDING) {
                                withTimeoutOrNull((timeout - (lastEventTime - first.uptimeMillis)).coerceAtLeast(1)) { awaitPointerEvent() }
                            } else awaitPointerEvent()
                            if (event == null) { emit(press.hold()); pendingTarget = null; continue }
                            val primary = event.changes.firstOrNull { it.id == first.id }
                            lastEventTime = primary?.uptimeMillis ?: event.changes.first().uptimeMillis
                            val multiple = event.changes.any { it.id != first.id && (it.pressed || it.previousPressed) }
                            val hit = primary?.let { anchorGeometry.hit(it.position.x / currentDensity, it.position.y / currentDensity) }
                            val consumed = event.changes.any { it.isConsumed }
                            press.movement(hit, primary?.let { (it.position - first.position).getDistance() } ?: Float.MAX_VALUE, multiple, consumed)
                            if (consumed) press.cancel()
                            if (press.state == PressGesture.State.TRANSFORMING && !consumed && event.changes.any { it.pressed }) {
                                transformed = true
                                val focus = event.calculateCentroid(useCurrent = false)
                                if (focus != Offset.Unspecified) {
                                    val pan = event.calculatePan()
                                    viewport = currentGeometry.copy(viewport = viewport).transform(event.calculateZoom(), pan.x / currentDensity,
                                        pan.y / currentDensity, focus.x / currentDensity, focus.y / currentDensity)
                                }
                            }
                            pendingTarget = press.pendingTarget
                            if (event.changes.none { it.pressed }) {
                                if (primary != null && primary.changedToUpIgnoreConsumed() && !consumed)
                                    emit(press.release(lastEventTime - first.uptimeMillis))
                                if (transformed) currentSave(viewport)
                                event.changes.forEach { it.consume() }; break
                            }
                            if (press.state != PressGesture.State.PENDING) event.changes.forEach { it.consume() }
                        }
                    } finally { pendingTarget = null }
                }
            }) {
            Canvas(Modifier.fillMaxSize()) {
                val cellSize = geometry.cell * density
                val gap = (cellSize * .055f).coerceAtMost(3 * density)
                val rounding = CornerRadius(cellSize * .15f)
                game.cells.forEachIndexed { index, storedCell ->
                    val preview = flagPreview
                    val cell = if (preview?.first == index && !storedCell.revealed) storedCell.copy(flagged = preview.second.flagged, questioned = preview.second.questioned) else storedCell
                    val x = (geometry.left + index % game.config.columns * geometry.cell) * density
                    val y = (geometry.top + index / game.config.columns * geometry.cell) * density
                    if (x + cellSize < 0 || y + cellSize < 0 || x > size.width || y > size.height) return@forEachIndexed
                    val origin = Offset(x + gap, y + gap)
                    val tileSize = Size(cellSize - gap * 2, cellSize - gap * 2)
                    val mineShown = cell.mine && game.finished
                    val wrongFlag = cell.flagged && !cell.mine && game.status == GameStatus.LOST
                    val fill = when {
                        index == game.explodedIndex -> Coral
                        wrongFlag -> if (dark) Color(0xFF693D41) else Color(0xFFFFDAD4)
                        cell.flagged -> if (dark) Color(0xFF51456B) else Color(0xFFFFE3DA)
                        mineShown -> if (dark) Color(0xFF4A3D56) else Color(0xFFE7DFF4)
                        cell.revealed -> if (dark) Color(0xFF20222D) else Color(0xFFF7F8FC)
                        else -> if (dark) Color(0xFF45415E) else Color(0xFFDBD5F5)
                    }
                    if (!cell.revealed) drawRoundRect(if (dark) Color(0xFF12121C) else Color(0xFFC4BCE8), origin + Offset(0f, cellSize * .035f), tileSize, rounding)
                    drawRoundRect(fill, origin, tileSize, rounding)
                    if (index == pendingTarget || index == hintIndex) drawRoundRect(colors.primary, origin, tileSize, rounding,
                        style = Stroke((2 * density).coerceAtMost(cellSize * .1f)))
                    val center = Offset(x + cellSize / 2, y + cellSize / 2)
                    when {
                        cell.flagged && !wrongFlag -> {
                            val flag = Path().apply {
                                moveTo(x + cellSize * .43f, y + cellSize * .25f)
                                lineTo(x + cellSize * .73f, y + cellSize * .25f)
                                lineTo(x + cellSize * .63f, y + cellSize * .40f)
                                lineTo(x + cellSize * .73f, y + cellSize * .53f)
                                lineTo(x + cellSize * .43f, y + cellSize * .53f); close()
                            }
                            drawPath(flag, if (dark) Coral else Color(0xFFD04B3D))
                            drawLine(if (dark) Color.White else Ink, Offset(x + cellSize * .4f, y + cellSize * .23f), Offset(x + cellSize * .4f, y + cellSize * .75f), cellSize * .055f)
                            drawLine(if (dark) Color.White else Ink, Offset(x + cellSize * .28f, y + cellSize * .76f), Offset(x + cellSize * .58f, y + cellSize * .76f), cellSize * .055f)
                        }
                        mineShown -> {
                            val ink = if (dark) Color(0xFFF1DDEA) else Ink
                            drawCircle(ink, cellSize * .17f, center)
                            for (v in listOf(Offset(1f, 0f), Offset(0f, 1f), Offset(.7f, .7f), Offset(.7f, -.7f)))
                                drawLine(ink, center - v * cellSize * .27f, center + v * cellSize * .27f, cellSize * .05f)
                            drawCircle(Color.White, cellSize * .04f, center + Offset(-cellSize * .055f, -cellSize * .055f))
                        }
                        wrongFlag -> {
                            drawLine(Coral, center - Offset(cellSize * .17f, cellSize * .17f), center + Offset(cellSize * .17f, cellSize * .17f), cellSize * .06f)
                            drawLine(Coral, center - Offset(-cellSize * .17f, cellSize * .17f), center + Offset(-cellSize * .17f, cellSize * .17f), cellSize * .06f)
                        }
                        cell.questioned && !game.finished -> {
                            paint.textSize = cellSize * .52f
                            paint.color = colors.onSurface.toArgb()
                            drawContext.canvas.nativeCanvas.drawText("?", center.x, center.y - (paint.ascent() + paint.descent()) / 2, paint)
                        }
                        cell.revealed && cell.adjacent > 0 -> {
                            paint.textSize = cellSize * .48f
                            paint.color = numberColors[cell.adjacent].toArgb()
                            drawContext.canvas.nativeCanvas.drawText(cell.adjacent.toString(), center.x, center.y - (paint.ascent() + paint.descent()) / 2, paint)
                        }
                    }
                }
            }
            // Virtual cell targets follow the exact same transform as the canvas, including zoom.
            game.cells.forEachIndexed { index, cell ->
                val x = geometry.left + index % game.config.columns * geometry.cell
                val y = geometry.top + index / game.config.columns * geometry.cell
                if (x + geometry.cell > 0 && y + geometry.cell > 0 && x < geometry.width && y < geometry.height) {
                    Box(Modifier.offset { IntOffset((x * density).roundToInt(), (y * density).roundToInt()) }
                        .size(geometry.cell.dp).semantics {
                            contentDescription = "Row ${index / game.config.columns + 1}, column ${index % game.config.columns + 1}"
                            stateDescription = when {
                                game.finished && cell.mine -> "Mine"
                                cell.flagged -> "Flagged"
                                cell.questioned -> "Question mark"
                                !cell.revealed -> "Covered"
                                cell.adjacent == 0 -> "Empty"
                                else -> "${cell.adjacent} adjacent mines"
                            }
                            if (!game.finished && inputEnabled) {
                                if (!cell.revealed || tapToChord) onClick(if (cell.revealed) "Reveal surrounding cells" else if (game.flagMode) "Toggle flag" else "Reveal") { onCell(CellIntent(game.id, index, if (cell.revealed) CellAction.CHORD else if (game.flagMode) CellAction.TOGGLE_FLAG else CellAction.REVEAL, game.flagMode, fromTap = cell.revealed)); true }
                                customActions = if (!cell.revealed) listOf(CustomAccessibilityAction(if (questionMarks) "Cycle flag, question mark, clear" else "Toggle flag") { onCell(CellIntent(game.id, index, CellAction.TOGGLE_FLAG, game.flagMode)); true })
                                else listOf(CustomAccessibilityAction("Reveal surrounding cells") { onCell(CellIntent(game.id, index, CellAction.CHORD, game.flagMode)); true })
                            }
                        }.testTag("${tagPrefix}cell-$index"))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = {
                viewport = geometry.transform(1 / 1.3f, 0f, 0f, geometry.width / 2, geometry.height / 2); onViewport(viewport)
            }, modifier = Modifier.semantics { contentDescription = "Zoom out" }) { Text("−") }
            TextButton(onClick = {
                viewport = Viewport(centerColumn = game.config.columns / 2f, centerRow = game.config.rows / 2f); onViewport(viewport)
            }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Fit board", style = MaterialTheme.typography.labelMedium) }
            IconButton(onClick = {
                viewport = geometry.transform(1.3f, 0f, 0f, geometry.width / 2, geometry.height / 2); onViewport(viewport)
            }, modifier = Modifier.semantics { contentDescription = "Zoom in" }) { Text("+") }
            controls()
        }
    }
}
