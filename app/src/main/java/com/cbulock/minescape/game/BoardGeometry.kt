package com.cbulock.minescape.game

import kotlin.math.min
import kotlin.math.floor

/** All coordinates are logical dp. Drawing, hit tests and semantics use this same transform. */
data class BoardGeometry(val config: BoardConfig, val width: Float, val height: Float, val viewport: Viewport) {
    val fit = min(width / config.columns, height / config.rows).coerceIn(1f, 64f)
    val cell = (if (viewport.cellDp == 0f) fit else viewport.cellDp).coerceIn(fit, 80f)
    private fun center(value: Float, count: Int, extent: Float): Float =
        if (count * cell <= extent) count / 2f else value.coerceIn(extent / cell / 2, count - extent / cell / 2)
    val centerX = center(viewport.centerColumn, config.columns, width)
    val centerY = center(viewport.centerRow, config.rows, height)
    val left = width / 2 - centerX * cell
    val top = height / 2 - centerY * cell
    fun hit(x: Float, y: Float): Int? {
        if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f || x >= width || y >= height) return null
        val col = floor((x - left) / cell).toInt()
        val row = floor((y - top) / cell).toInt()
        return if (col in 0 until config.columns && row in 0 until config.rows) row * config.columns + col else null
    }
    fun transform(factor: Float, panX: Float, panY: Float, focusX: Float, focusY: Float): Viewport {
        val newCell = (cell * factor).coerceIn(fit, 80f)
        val boardX = (focusX - left) / cell
        val boardY = (focusY - top) / cell
        val next = Viewport(newCell,
            boardX - (focusX + panX - width / 2) / newCell,
            boardY - (focusY + panY - height / 2) / newCell)
        val clamped = copy(viewport = next)
        return next.copy(centerColumn = clamped.centerX, centerRow = clamped.centerY)
    }
}
