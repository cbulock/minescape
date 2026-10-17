package com.cbulock.minescape.game

import org.junit.Assert.*
import org.junit.Test

class BoardGeometryTest {
    private val config = BoardConfig.preset(Difficulty.EXPERT)
    private val geometry = BoardGeometry(config, 360f, 400f, Viewport(centerColumn = 15f, centerRow = 8f))
    @Test fun `fit shows whole board and edge hits stay exact`() {
        assertEquals(12f, geometry.cell)
        assertEquals(0, geometry.hit(geometry.left + .1f, geometry.top + .1f))
        assertEquals(479, geometry.hit(geometry.left + 359.9f, geometry.top + 191.9f))
        assertNull(geometry.hit(geometry.left - .1f, geometry.top))
        assertNull(geometry.hit(geometry.left + 360f, geometry.top))
    }
    @Test fun `zoom preserves the square under the fingers when unconstrained`() {
        val first = geometry.copy(viewport = Viewport(40f, 15f, 8f))
        val hit = first.hit(130f, 160f)
        val next = first.copy(viewport = first.transform(1.2f, 0f, 0f, 130f, 160f))
        assertEquals(hit, next.hit(130f, 160f))
    }
    @Test fun `zoom stays within limits`() {
        assertEquals(80f, geometry.transform(999f, 0f, 0f, 180f, 200f).cellDp)
        assertEquals(12f, geometry.transform(.001f, 0f, 0f, 180f, 200f).cellDp)
    }
    @Test fun `repeated zoom out stops at fit and recenters the whole board`() {
        val beginner = BoardConfig.preset(Difficulty.BEGINNER)
        var board = BoardGeometry(beginner, 360f, 500f, Viewport(80f, 2.5f, 6f))
        repeat(20) { board = board.copy(viewport = board.transform(1 / 1.3f, 20f, -10f, 90f, 110f)) }
        assertEquals(40f, board.cell)
        assertEquals(board.fit, board.viewport.cellDp)
        assertEquals(0f, board.left)
        assertEquals(70f, board.top)
        assertEquals(0, board.hit(.1f, 70.1f))
        assertEquals(80, board.hit(359.9f, 429.9f))
    }
    @Test fun `restored viewport cannot shrink below fit after layout changes`() {
        val beginner = BoardConfig.preset(Difficulty.BEGINNER)
        val board = BoardGeometry(beginner, 720f, 450f, Viewport(24f, 1f, 8f))
        assertEquals(50f, board.cell)
        assertEquals(135f, board.left)
        assertEquals(0f, board.top)
        assertEquals(50f, board.transform(.01f, 0f, 0f, 300f, 200f).cellDp)
    }
    @Test fun `panning cannot lose the board`() {
        val enlarged = geometry.copy(viewport = Viewport(40f, 15f, 8f))
        val next = enlarged.copy(viewport = enlarged.transform(1f, 90000f, -90000f, 180f, 200f))
        assertEquals(0f, next.left)
        assertEquals(next.height - config.rows * next.cell, next.top)
    }
    @Test fun `rotation clamps old viewport to new bounds`() {
        val rotated = geometry.copy(width = 740f, height = 200f, viewport = Viewport(40f, 2f, 99f))
        assertEquals(0f, rotated.left)
        assertEquals(200f - 16 * 40, rotated.top)
    }
    @Test fun `clipped cells outside viewport cannot be hit`() {
        val zoomed = geometry.copy(viewport = Viewport(47.5f, 15f, 8f))
        assertTrue(zoomed.left < 0)
        for (point in listOf(-.1f to 100f, 360f to 100f, 100f to -.1f, 100f to 400f, Float.NaN to 10f))
            assertNull(zoomed.hit(point.first, point.second))
    }
    @Test fun `fractional zoom coordinates retain exact indices at different densities`() {
        val zoomed = geometry.copy(viewport = Viewport(47.5f, 15f, 8f))
        for (density in listOf(1f, 1.5f, 2f, 3.5f)) for (index in 0 until config.size) {
            val x = zoomed.left + (index % config.columns + .5f) * zoomed.cell
            val y = zoomed.top + (index / config.columns + .5f) * zoomed.cell
            if (x in 0f..<zoomed.width && y in 0f..<zoomed.height)
                assertEquals(index, zoomed.hit((x * density) / density, (y * density) / density))
        }
    }
}
