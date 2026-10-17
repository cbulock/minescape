package com.cbulock.minescape.game

import com.cbulock.minescape.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.random.Random

class LearningFeaturesTest {
    private val engine = GameEngine(Random(8))
    private fun opening() = engine.reveal(Game(BoardConfig.preset(Difficulty.BEGINNER), mode = PlayMode.PRACTICE), 40)
    private fun win(time: Long, generation: GenerationMode = GenerationMode.CLASSIC): Game {
        var game = opening().copy(mode = PlayMode.NORMAL, generation = generation)
        game.cells.indices.filter { !game.cells[it].mine }.forEach { game = engine.reveal(game, it) }
        return game.copy(elapsedMillis = time)
    }
    @Test fun `question marks cycle without becoming flags and can be cleared when disabled`() {
        val initial = Game(BoardConfig(5, 5, 1))
        val flagged = engine.act(initial, 0, CellAction.TOGGLE_FLAG, true)
        val questioned = engine.act(flagged, 0, CellAction.TOGGLE_FLAG, true)
        assertEquals(1, flagged.flags); assertEquals(0, questioned.flags)
        assertTrue(questioned.cells[0].questioned); assertFalse(questioned.cells[0].flagged)
        assertEquals(initial.cells, engine.act(questioned, 0, CellAction.TOGGLE_FLAG, true).cells)
        assertEquals(initial.cells, engine.act(questioned, 0, CellAction.TOGGLE_FLAG, false).cells)
        AppData(game = questioned).validate()
    }
    @Test fun `question marks do not satisfy chords and a reveal removes them undoably`() {
        val number = Tutorial.game(1)
        val flagged = engine.act(number, 0, CellAction.TOGGLE_FLAG, true)
        val questioned = engine.act(flagged, 0, CellAction.TOGGLE_FLAG, true)
        assertEquals(questioned, engine.act(questioned, 6, CellAction.CHORD, true))
        val safe = engine.act(engine.act(number, 1, CellAction.TOGGLE_FLAG, true), 1, CellAction.TOGGLE_FLAG, true)
        val revealed = engine.act(safe, 1, CellAction.REVEAL, true)
        assertTrue(revealed.cells[1].revealed); assertFalse(revealed.cells[1].questioned)
        assertEquals(safe.cells, engine.undo(revealed).cells)
        assertEquals(number.cells, engine.undo(engine.undo(questioned)).cells)
        val data = AppData(game = revealed, preferences = Preferences(questionMarks = true, holdMillis = 800, tapToChord = false))
        assertEquals(data, Backups.preview(ByteArrayInputStream(Backups.export(data))).data)
    }
    @Test fun `retry retains exact layout and opening but resets assistance identity time and records`() {
        val original = opening()
        val loss = engine.act(original, original.cells.indexOfFirst { it.mine }, CellAction.REVEAL).copy(elapsedMillis = 55000, hintsUsed = 4)
        val retry = engine.retryAsPractice(loss)
        assertNotEquals(loss.id, retry.id); assertEquals(PlayMode.PRACTICE, retry.mode)
        assertEquals(original.cells, retry.cells); assertEquals(original.openingCells, retry.openingCells)
        assertEquals(0L, retry.elapsedMillis); assertEquals(0, retry.hintsUsed); assertEquals(0, retry.minesHit)
        assertTrue(retry.undo.isEmpty()); assertEquals(GameStatus.PLAYING, retry.status)
        val lostAgain = engine.act(retry, retry.cells.indexOfFirst { it.mine }, CellAction.REVEAL)
        assertEquals(retry.cells, engine.undo(lostAgain).cells)
        val data = AppData(game = lostAgain).recordResult()
        assertTrue(data.statistics.isEmpty()); assertTrue(data.results.isEmpty())
        assertEquals(data, Backups.preview(ByteArrayInputStream(Backups.export(data))).data)
    }
    @Test fun `no guess retry preserves the actual opening with pre opening markers`(): Unit = runBlocking {
        var ready = Game(BoardConfig.preset(Difficulty.BEGINNER), generation = GenerationMode.NO_GUESS)
        ready = engine.toggleFlag(ready, 41)
        ready = engine.toggleFlag(engine.toggleFlag(ready, 0, true), 0, true)
        val generated = NoGuessGenerator.generate(ready, 40, seed = 19)!!
        assertTrue(generated.cells[41].flagged); assertFalse(generated.cells[41].revealed)
        assertEquals(generated.cells.indices.filter { generated.cells[it].revealed }, generated.openingCells)
        val loss = engine.reveal(generated, generated.cells.indexOfFirst { it.mine && !it.flagged })
        val retry = engine.retryAsPractice(loss)
        assertEquals(generated.cells.map { it.copy(flagged = false, questioned = false) }, retry.cells)
        assertEquals(GenerationMode.NO_GUESS, retry.generation)
        AppData(game = retry).validate()
    }
    @Test fun `older layouts retry with a safe opening and ready games cannot retry`() {
        val base = opening()
        val terminal = engine.reveal(base, base.cells.indexOfFirst { it.mine }).copy(openingIndex = null, openingCells = emptyList())
        val retry = engine.retryAsPractice(terminal)
        assertEquals(terminal.cells.map { it.mine }, retry.cells.map { it.mine })
        assertTrue(retry.cells.any { it.revealed }); assertFalse(retry.cells.any { it.revealed && it.mine })
        AppData(game = retry).validate()
        assertThrows(IllegalArgumentException::class.java) { engine.retryAsPractice(Game(BoardConfig(5, 5, 1))) }
    }
    @Test fun `personal best compares prior preset mode record strictly and survives restoring`() {
        var data = AppData(game = win(12000)).recordResult()
        assertTrue(data.results.last().newBest); assertNull(data.results.last().previousBestMillis)
        data = data.copy(game = win(10000)).recordResult()
        assertTrue(data.results.last().newBest); assertEquals(12000L, data.results.last().previousBestMillis)
        data = data.copy(game = win(10000)).recordResult()
        assertFalse(data.results.last().newBest)
        data = data.copy(game = win(14000)).recordResult()
        assertFalse(data.results.last().newBest); assertEquals(10000L, data.results.last().previousBestMillis)
        data = data.copy(game = win(18000, GenerationMode.NO_GUESS)).recordResult()
        assertTrue(data.results.last().newBest); assertNull(data.results.last().previousBestMillis)
        val restored = Backups.preview(ByteArrayInputStream(Backups.export(data))).data
        assertEquals(data, restored.recordResult())
        assertEquals(10000L, data.statistics[Difficulty.BEGINNER]?.bestMillis)
    }
    @Test fun `old saves default new fields and migration retains totals`(): Unit = runBlocking {
        val old = """{"schemaVersion":2,"game":{"config":{"columns":5,"rows":5,"mines":1},"cells":[${List(25) { "{}" }.joinToString(",")}]},"statistics":{"BEGINNER":{"wins":4,"bestMillis":15000}}}"""
        val restored = AppDataSerializer.readFrom(ByteArrayInputStream(old.toByteArray()))
        assertEquals(3, restored.schemaVersion); assertTrue(restored.migrated)
        assertEquals(4, restored.statistics[Difficulty.BEGINNER]?.wins)
        assertEquals(Preferences(), restored.preferences); assertTrue(restored.game!!.openingCells.isEmpty())
        assertTrue(restored.game!!.cells.none { it.questioned })
        assertThrows(IllegalArgumentException::class.java) { restored.copy(preferences = Preferences(holdMillis = 1)).validate() }
        val invalid = Json.encodeToString(Backup.serializer(), Backup(exportedAt = "2026-10-09T00:00:00Z", data = restored.copy(preferences = Preferences(holdMillis = 9000))))
        assertThrows(IllegalArgumentException::class.java) { Backups.preview(ByteArrayInputStream(invalid.toByteArray())) }
    }
    @Test fun `tutorial accepts only expected actions and completes by actual chording`() {
        for (step in 0..2) {
            assertEquals(step, Tutorial.advance(step, CellIntent("other", Tutorial.targets[step], Tutorial.actions[step], false)))
            assertEquals(step, Tutorial.advance(step, CellIntent("tutorial-$step", 24, Tutorial.actions[step], false)))
            assertEquals(step + 1, Tutorial.advance(step, CellIntent("tutorial-$step", Tutorial.targets[step], Tutorial.actions[step], false)))
        }
        assertEquals(GameStatus.WON, Tutorial.game(3).status)
        assertEquals(24, Tutorial.game(3).cleared)
        assertTrue(AppData(game = Tutorial.game(3)).recordResult().results.isEmpty())
    }
}
