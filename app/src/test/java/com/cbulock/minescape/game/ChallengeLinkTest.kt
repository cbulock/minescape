package com.cbulock.minescape.game

import com.cbulock.minescape.data.AppData
import com.cbulock.minescape.data.recordResult
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class ChallengeLinkTest {
    private val today = LocalDate.of(2026, 11, 1)
    @Test fun `regular round trip retains exact opening but excludes progress and records`() {
        val engine = GameEngine(Random(17))
        val opening = engine.reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
        val progressed = engine.toggleFlag(opening, opening.cells.indexOfFirst { it.mine }).copy(elapsedMillis = 35000)
        val link = ChallengeLink.encode(progressed)
        val restored = (ChallengeLink.parse(link, today) as SharedChallenge.Board).game()
        assertEquals(opening.cells, restored.cells)
        assertEquals(opening.openingCells, restored.openingCells)
        assertEquals(0L, restored.elapsedMillis)
        assertEquals(PlayMode.PRACTICE, restored.mode)
        assertNotEquals(opening.id, restored.id)
        assertTrue(AppData(game = restored.copy(status = GameStatus.LOST)).recordResult().statistics.isEmpty())
        assertEquals(restored.cells, (ChallengeLink.parse(link.replace("https://cbulock.github.io/minescape/play/", "minescape://play/"), today) as SharedChallenge.Board).game().cells)
    }
    @Test fun `large custom board is bounded and round trips`() {
        val board = GameEngine(Random(2)).reveal(Game(BoardConfig(50, 50, 875)), 0)
        val link = ChallengeLink.encode(board)
        assertTrue(link.length < 1200)
        assertEquals(board.cells, (ChallengeLink.parse(link, today) as SharedChallenge.Board).game().cells)
    }
    @Test fun `daily links carry only version and date`() {
        val link = ChallengeLink.daily("2026-10-31")
        assertEquals(SharedChallenge.Daily("2026-10-31"), ChallengeLink.parse(link, today))
        rejects(ChallengeLink.daily("2026-11-02"))
        rejects(link.replace("d1.", "d2."))
        rejects(link.replace("2026-10-31", "2026-02-30"))
    }
    @Test fun `rejects foreign hosts corruption huge inputs and ungenerated boards`() {
        val board = GameEngine(Random(3)).reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
        val link = ChallengeLink.encode(board)
        rejects(link.replace("cbulock.github.io", "example.com"))
        rejects(link.dropLast(5) + "AAAAA")
        rejects(link + "a".repeat(10000))
        rejects("https://cbulock.github.io/minescape/play/#b1.")
        try { ChallengeLink.encode(Game(BoardConfig.preset(Difficulty.BEGINNER))); fail() } catch (_: IllegalArgumentException) { }
    }
    private fun rejects(value: String) {
        try { ChallengeLink.parse(value, today); fail("Accepted $value") } catch (_: IllegalArgumentException) { }
    }
}
