package com.cbulock.minescape.game

import org.junit.Assert.*
import org.junit.Test

class VisualHintTest {
    @Test fun `evidence includes clues and covered region and ignores hidden mine truth`() {
        val config = BoardConfig(5, 5, 1)
        // This is a public-knowledge fixture: the solver must never inspect hidden truth.
        val game = Game(config, status = GameStatus.PLAYING, cells = List(25) { i ->
            Cell(revealed = i == 0, adjacent = if (i == 0) 0 else 7)
        })
        val hint = requireNotNull(LogicSolver.hint(game))
        assertFalse(hint.mine)
        assertEquals(setOf(0), hint.clueIndices)
        assertEquals(setOf(1, 5, 6), hint.coveredIndices)
        val changed = game.copy(cells = game.cells.mapIndexed { i, cell ->
            if (cell.revealed) cell else cell.copy(mine = i % 2 == 0, adjacent = 3, flagged = true)
        })
        assertEquals(hint, LogicSolver.hint(changed))
    }
    @Test fun `subset deductions retain both supporting clues`() {
        val game = Game(BoardConfig(5, 5, 8), status = GameStatus.PLAYING,
            cells = List(25) { i -> Cell(revealed = i == 0 || i == 1, adjacent = if (i == 0 || i == 1) 1 else 0) })
        val hint = requireNotNull(LogicSolver.hint(game))
        assertFalse(hint.mine)
        assertTrue(hint.clueIndices.containsAll(listOf(0, 1)))
        assertTrue(hint.index in hint.coveredIndices)
        assertTrue(hint.coveredIndices.all { !game.cells[it].revealed })
    }
}
