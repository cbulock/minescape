package com.cbulock.minescape.game

import kotlin.math.min
import kotlin.math.floor

/** All coordinates are logical dp. Drawing, hit tests and semantics use this same transform. */
data class BoardGeometry(val config: BoardConfig, val width: Float, val height: Float, val viewport: Viewport,
                         val autoOrient: Boolean = true) {
    // Transpose the presentation only; cell indices, neighbors and saved coordinates stay logical.
    val transposed = autoOrient && min(width / config.rows, height / config.columns) >
        min(width / config.columns, height / config.rows)
    val columns = if (transposed) config.rows else config.columns
    val rows = if (transposed) config.columns else config.rows
    fun column(index: Int) = if (transposed) index / config.columns else index % config.columns
    fun row(index: Int) = if (transposed) index % config.columns else index / config.columns
    val fit = min(width / columns, height / rows).coerceIn(1f, 80f)
    val cell = (if (viewport.cellDp == 0f) fit else viewport.cellDp).coerceIn(fit, 80f)
    private fun center(value: Float, count: Int, extent: Float): Float =
        if (count * cell <= extent) count / 2f else value.coerceIn(extent / cell / 2, count - extent / cell / 2)
    val centerX = center(if (transposed) viewport.centerRow else viewport.centerColumn, columns, width)
    val centerY = center(if (transposed) viewport.centerColumn else viewport.centerRow, rows, height)
    val left = width / 2 - centerX * cell
    val top = height / 2 - centerY * cell
    fun hit(x: Float, y: Float): Int? {
        if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f || x >= width || y >= height) return null
        val col = floor((x - left) / cell).toInt()
        val row = floor((y - top) / cell).toInt()
        return if (col in 0 until columns && row in 0 until rows)
            if (transposed) col * config.columns + row else row * config.columns + col
        else null
    }
    fun transform(factor: Float, panX: Float, panY: Float, focusX: Float, focusY: Float): Viewport {
        val newCell = (cell * factor).coerceIn(fit, 80f)
        val boardX = (focusX - left) / cell
        val boardY = (focusY - top) / cell
        val x = boardX - (focusX + panX - width / 2) / newCell
        val y = boardY - (focusY + panY - height / 2) / newCell
        val next = Viewport(newCell, if (transposed) y else x, if (transposed) x else y)
        val clamped = copy(viewport = next)
        return next.copy(centerColumn = if (transposed) clamped.centerY else clamped.centerX,
            centerRow = if (transposed) clamped.centerX else clamped.centerY)
    }
}
