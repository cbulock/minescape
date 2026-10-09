package com.camer.minesweeper.game

import com.camer.minesweeper.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.*
import kotlin.random.Random

class FeaturesTest {
    private val engine = GameEngine(Random(3))
    private fun finish(won: Boolean, mode: PlayMode = PlayMode.NORMAL, generation: GenerationMode = GenerationMode.CLASSIC): Game {
        var game = engine.reveal(Game(BoardConfig.preset(Difficulty.BEGINNER), mode = mode, generation = generation), 40)
        if (won) game.cells.indices.filter { !game.cells[it].mine }.forEach { game = engine.reveal(game, it) }
        else game = engine.reveal(game, game.cells.indexOfFirst { it.mine })
        return game.copy(elapsedMillis = 8000)
    }
    private fun catalog(): DailyChallenge {
        val bytes = File("src/main/assets/daily-v1.json").readBytes()
        assertEquals("db1dcdb32040b6d2e7083b10420660ff58cd8ed0bd23e7be91bf644c0153f0b1",
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        return DailyChallenge(Json.decodeFromString(bytes.decodeToString()))
    }
    @Test fun `every frozen daily layout is independently solvable`() {
        val daily = catalog()
        for (day in 0L until 366L) {
            val game = daily.game(LocalDate.ofEpochDay(day).toString())
            assertEquals(40, game.cells.count { it.mine })
            assertTrue("Daily layout $day requires a guess", LogicSolver.solves(game))
        }
    }
    @Test fun `daily transformations remain solvable and date is UTC`() {
        val daily = catalog()
        for (cycle in 0L..7L) assertTrue(LogicSolver.solves(daily.game(LocalDate.ofEpochDay(cycle * 366).toString())))
        val a = daily.game("2026-10-09"); val b = daily.game("2026-10-09")
        assertEquals(a.cells, b.cells); assertNotEquals(a.id, b.id)
        val layouts = Json.decodeFromString<List<List<Int>>>(File("src/main/assets/daily-v1.json").readText())
        assertEquals("2026-10-10", DailyChallenge(layouts, Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneId.of("America/New_York"))).today())
        assertEquals("2026-10-09", DailyChallenge(layouts, Clock.fixed(Instant.parse("2026-10-09T23:59:59Z"), ZoneId.of("Asia/Tokyo"))).today())
    }
    @Test fun `no guess presets certify opening and bounded failure`() = runBlocking {
        for (difficulty in listOf(Difficulty.BEGINNER, Difficulty.INTERMEDIATE, Difficulty.EXPERT)) {
            val game = Game(BoardConfig.preset(difficulty), generation = GenerationMode.NO_GUESS)
            val generated = NoGuessGenerator.generate(game, 0, seed = 19)
            assertNotNull("Could not generate $difficulty", generated)
            assertTrue(LogicSolver.solves(generated!!))
            (GameEngine.neighbors(game.config, 0) + 0).forEach { assertFalse(generated.cells[it].mine) }
            assertNull(NoGuessGenerator.generate(game, 0, maxCandidates = 0))
            assertNull(NoGuessGenerator.generate(game, 0, maxMillis = 0))
        }
        val job = launch(start = CoroutineStart.LAZY) { NoGuessGenerator.generate(Game(BoardConfig.preset(Difficulty.EXPERT)), 0) }
        job.cancel(); job.join(); assertTrue(job.isCancelled)
    }
    @Test fun `hints ignore player flags and hidden mine truth`() {
        val config = BoardConfig(5, 5, 3)
        val cells = List(25) { i -> Cell(mine = i in setOf(0, 4, 24), adjacent = 0, revealed = i == 12, flagged = i == 6) }
        val game = Game(config, cells = cells, status = GameStatus.PLAYING)
        val hint = LogicSolver.hint(game)!!
        assertFalse(hint.mine)
        assertEquals(6, hint.index) // A wrong player flag must not hide a logically safe move.
        val changedHiddenTruth = game.copy(cells = cells.mapIndexed { i, cell -> if (i == 12) cell else cell.copy(mine = !cell.mine, adjacent = 8) })
        assertEquals(hint, LogicSolver.hint(changedHiddenTruth))
    }
    @Test fun `practice fatal move and chord undo preserve time and counters`() {
        var game = engine.act(Game(BoardConfig.preset(Difficulty.BEGINNER), mode = PlayMode.PRACTICE), 40, CellAction.REVEAL)
        assertTrue(game.undo.isEmpty())
        val opening = game.copy(elapsedMillis = 9876)
        game = engine.act(opening, opening.cells.indexOfFirst { it.mine }, CellAction.REVEAL)
        assertEquals(GameStatus.LOST, game.status); assertEquals(1, game.minesHit)
        val restored = engine.undo(game)
        assertEquals(opening.cells, restored.cells); assertEquals(9876L, restored.elapsedMillis)
        assertEquals(1, restored.undosUsed); assertEquals(1, restored.minesHit)
        AppData(game = game).validate(); AppData(game = restored).validate()
        assertTrue(AppData(game = game).recordResult().statistics.isEmpty())
        val index = restored.cells.indexOfFirst { !it.revealed }
        val flagged = engine.act(restored, index, CellAction.TOGGLE_FLAG)
        assertEquals(restored.cells, engine.undo(flagged).cells)
    }
    @Test fun `practice move history is bounded and survives backup`() {
        var game = engine.reveal(Game(BoardConfig.preset(Difficulty.BEGINNER), mode = PlayMode.PRACTICE), 40)
        val index = game.cells.indexOfFirst { !it.revealed }
        repeat(120) { game = engine.act(game, index, CellAction.TOGGLE_FLAG) }
        assertEquals(100, game.undo.size)
        val data = AppData(game = game)
        assertEquals(data, Backups.preview(ByteArrayInputStream(Backups.export(data))).data)
    }
    @Test fun `streaks scoring modes and history are independent and exactly once`() {
        var data = AppData(game = finish(true)).recordResult()
        data = data.copy(game = finish(true)).recordResult()
        assertEquals(2, data.statistics[Difficulty.BEGINNER]?.currentStreak)
        assertEquals(data, data.recordResult())
        data = data.copy(game = finish(false)).recordResult()
        assertEquals(0, data.statistics[Difficulty.BEGINNER]?.currentStreak)
        assertEquals(2, data.statistics[Difficulty.BEGINNER]?.bestStreak)
        data = data.copy(game = finish(true, generation = GenerationMode.NO_GUESS)).recordResult()
        assertEquals(1, data.noGuessStatistics[Difficulty.BEGINNER]?.wins)
        assertEquals(2, data.statistics[Difficulty.BEGINNER]?.wins)
        assertEquals(4, data.results.size)
        assertEquals(100f * 2 / 3, data.statistics[Difficulty.BEGINNER]!!.winPercent!!, .01f)
        repeat(105) { data = data.copy(game = finish(true)).recordResult() }
        assertEquals(100, data.results.size)
    }
    @Test fun `daily terminal result never affects normal statistics`() {
        var game = catalog().game("2026-10-09")
        game = engine.reveal(game, game.cells.indexOfFirst { it.mine })
        val data = AppData(dailyGame = game, activeSlot = ActiveSlot.DAILY,
            dailyRecords = mapOf("2026-10-09" to DailyRecord("2026-10-09", DailyOutcome.IN_PROGRESS))).recordResult()
        assertTrue(data.statistics.isEmpty()); assertTrue(data.results.isEmpty())
        assertEquals(DailyOutcome.LOST, data.dailyRecords["2026-10-09"]?.outcome)
        assertEquals(data, data.recordResult()); data.validate()
    }
    @Test fun `version one migration retains game and totals without inventing streaks`() = runBlocking {
        val old = AppData(schemaVersion = 1, game = engine.reveal(Game(BoardConfig(5, 5, 3)), 12),
            statistics = mapOf(Difficulty.BEGINNER to Statistics(12, 5, 1234)))
        val output = ByteArrayOutputStream(); AppDataSerializer.writeTo(old, output)
        val restored = AppDataSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
        assertEquals(3, restored.schemaVersion); assertTrue(restored.migrated)
        assertEquals(old.game, restored.game); assertEquals(old.statistics, restored.statistics)
        assertTrue(restored.results.isEmpty()); assertEquals(0, restored.statistics[Difficulty.BEGINNER]?.bestStreak)
        assertEquals(restored, Backups.preview(ByteArrayInputStream(output.toByteArray())).data)
    }
    @Test fun `invalid future oversized and inconsistent backups are rejected`() {
        val invalid = listOf("broken", "{\"schemaVersion\":99}",
            "{\"formatVersion\":2,\"exportedAt\":\"2026-10-09T00:00:00Z\",\"data\":{}}",
            "{\"schemaVersion\":1,\"activeSlot\":\"DAILY\"}")
        invalid.forEach { text -> try { Backups.preview(ByteArrayInputStream(text.toByteArray())); fail(text) } catch (_: Exception) { } }
        try { Backups.preview(ByteArrayInputStream(ByteArray(Backups.MAX_BYTES + 1))); fail("oversized accepted") } catch (_: IllegalArgumentException) { }
        val game = engine.reveal(Game(BoardConfig(5, 5, 3), mode = PlayMode.PRACTICE), 12)
        try { AppData(game = game.copy(undo = listOf(UndoMove(listOf(999), emptyList(), GameStatus.PLAYING, -1)))).validate(); fail("bad undo accepted") } catch (_: IllegalArgumentException) { }
    }
}
