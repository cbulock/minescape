package com.cbulock.minescape.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private val engine = GameEngine(Random(123))
    private val config = BoardConfig(5, 5, 3)
    private fun field(mines: Set<Int> = setOf(0, 4, 24)): Game = Game(config, status = GameStatus.PLAYING,
        cells = List(25) { i -> Cell(mine = i in mines, adjacent = GameEngine.neighbors(config, i).count { it in mines }) })

    @Test fun `presets have the classic dimensions`() {
        assertEquals(BoardConfig(9, 9, 10, Difficulty.BEGINNER), BoardConfig.preset(Difficulty.BEGINNER))
        assertEquals(BoardConfig(16, 16, 40, Difficulty.INTERMEDIATE), BoardConfig.preset(Difficulty.INTERMEDIATE))
        assertEquals(BoardConfig(30, 16, 99, Difficulty.EXPERT), BoardConfig.preset(Difficulty.EXPERT))
    }
    @Test fun `custom limits and preset identity are validated`() {
        assertTrue(BoardConfig(5, 5, 8).isValid())
        assertTrue(BoardConfig(50, 50, 875).isValid())
        listOf(BoardConfig(4, 5, 1), BoardConfig(51, 50, 2), BoardConfig(5, 5, 0), BoardConfig(5, 5, 9), BoardConfig(9, 9, 10, Difficulty.EXPERT)).forEach { assertFalse(it.isValid()) }
    }
    @Test fun `boards are empty until the first reveal`() {
        val game = engine.toggleFlag(engine.newGame(config), 0)
        assertEquals(GameStatus.READY, game.status)
        assertTrue(game.cells.none { it.mine || it.revealed })
        assertEquals(game, engine.reveal(game, 0))
    }
    @Test fun `generation keeps every first neighborhood safe and exact mine count`() {
        for (seed in 0..39) for (first in listOf(0, 24, 12, 4, 20)) {
            val game = GameEngine(Random(seed)).reveal(Game(config), first)
            assertEquals(config.mines, game.cells.count { it.mine })
            (GameEngine.neighbors(config, first) + first).forEach { assertFalse(game.cells[it].mine) }
            assertEquals(0, game.cells[first].adjacent)
            game.cells.forEachIndexed { i, cell -> assertEquals(GameEngine.neighbors(config, i).count { game.cells[it].mine }, cell.adjacent) }
        }
    }
    @Test fun `seeded generation is reproducible and random seeds differ`() {
        val a = GameEngine(Random(1)).reveal(Game(config), 12).cells
        assertEquals(a, GameEngine(Random(1)).reveal(Game(config), 12).cells)
        assertNotEquals(a, GameEngine(Random(2)).reveal(Game(config), 12).cells)
    }
    @Test fun `neighbors do not wrap across edges`() {
        assertEquals(setOf(1, 5, 6), GameEngine.neighbors(config, 0).toSet())
        assertEquals(8, GameEngine.neighbors(config, 12).size)
        assertEquals(setOf(18, 19, 23), GameEngine.neighbors(config, 24).toSet())
    }
    @Test fun `empty areas expand and stop at flagged squares`() {
        val flagged = engine.toggleFlag(field(), 11)
        val game = engine.reveal(flagged, 12)
        assertTrue(game.cells[12].revealed)
        assertTrue(game.cells[6].revealed)
        assertFalse(game.cells[11].revealed)
        assertTrue(game.cells[11].flagged)
        assertTrue(game.cells.none { it.mine && it.revealed })
    }
    @Test fun `flags toggle but revealed cells cannot be flagged`() {
        val game = field()
        assertEquals(game, engine.toggleFlag(engine.toggleFlag(game, 3), 3))
        val opened = engine.reveal(game, 1)
        assertEquals(opened, engine.toggleFlag(opened, 1))
    }
    @Test fun `chording opens neighbors when flag count matches`() {
        var game = engine.reveal(field(), 1)
        assertEquals(game, engine.chord(game, 1))
        game = engine.toggleFlag(game, 0)
        val next = engine.chord(game, 1)
        assertTrue(next.cells[2].revealed)
        assertTrue(next.cells[6].revealed)
        assertFalse(next.cells[0].revealed)
    }
    @Test fun `incorrect flags can make a chord lose`() {
        val game = engine.toggleFlag(engine.reveal(field(), 1), 2)
        val next = engine.chord(game, 1)
        assertEquals(GameStatus.LOST, next.status)
        assertEquals(0, next.explodedIndex)
    }
    @Test fun `queued reveal intent never becomes a chord on an already revealed number`() {
        val game = engine.toggleFlag(engine.reveal(field(), 1), 2)
        assertEquals(game, engine.act(game, 1, CellAction.REVEAL))
        assertEquals(GameStatus.LOST, engine.act(game, 1, CellAction.CHORD).status)
    }
    @Test fun `revealing a mine loses and locks actions`() {
        val game = engine.reveal(field(), 0)
        assertEquals(GameStatus.LOST, game.status)
        assertEquals(game, engine.reveal(game, 1))
        assertEquals(game, engine.toggleFlag(game, 3))
        assertEquals(game, engine.chord(game, 1))
    }
    @Test fun `opening all safe cells wins without requiring flags`() {
        var game = field()
        game.cells.indices.filter { !game.cells[it].mine }.forEach { game = engine.reveal(game, it) }
        assertEquals(GameStatus.WON, game.status)
        assertEquals(0, game.flags)
        assertEquals(game, engine.reveal(game, 0))
    }
    @Test fun `out of bounds actions are ignored`() {
        val game = field()
        assertEquals(game, engine.reveal(game, -1))
        assertEquals(game, engine.toggleFlag(game, 25))
        assertEquals(game, engine.chord(game, 999))
    }
    @Test fun `maximum board generation and flood fill remain valid`() {
        for (mines in listOf(1, 875)) {
            val game = engine.reveal(Game(BoardConfig(50, 50, mines)), 1275)
            assertEquals(mines, game.cells.count { it.mine })
            assertFalse(game.cells[1275].mine)
            assertTrue(game.cells[1275].revealed)
        }
    }
}
