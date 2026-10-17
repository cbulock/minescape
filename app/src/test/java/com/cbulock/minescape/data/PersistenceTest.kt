package com.cbulock.minescape.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.cbulock.minescape.game.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.io.IOException
import java.io.OutputStream
import androidx.datastore.core.Serializer
import kotlin.random.Random

class PersistenceTest {
    private val engine = GameEngine(Random(4))
    private fun roundTrip(data: AppData): AppData = runBlocking {
        val out = ByteArrayOutputStream()
        AppDataSerializer.writeTo(data, out)
        AppDataSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))
    }
    @Test fun `ready game flags settings and viewport round trip`() {
        val game = engine.toggleFlag(Game(BoardConfig(5, 5, 3)), 3).copy(viewport = Viewport(48f, 2f, 3f))
        val state = AppData(game = game, preferences = Preferences(ThemeMode.DARK, false))
        assertEquals(state, roundTrip(state))
    }
    @Test fun `midgame saves actual mine placement and elapsed time`() {
        val game = engine.reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40).copy(elapsedMillis = 5678)
        val state = AppData(game = game)
        assertEquals(state, roundTrip(state))
    }
    @Test fun `result transaction records each finish once even after restoring`() {
        var game = engine.reveal(Game(BoardConfig.preset(Difficulty.BEGINNER)), 40)
        game.cells.indices.filter { !game.cells[it].mine }.forEach { game = engine.reveal(game, it) }
        val recorded = AppData(game = game.copy(elapsedMillis = 4321)).recordResult()
        assertEquals(1, recorded.statistics[Difficulty.BEGINNER]?.wins)
        assertEquals(4321L, recorded.statistics[Difficulty.BEGINNER]?.bestMillis)
        assertEquals(recorded, roundTrip(recorded).recordResult())
    }
    @Test fun `losses count and unfinished games do not`() {
        var game = engine.reveal(Game(BoardConfig.preset(Difficulty.EXPERT)), 200)
        assertTrue(AppData(game = game).recordResult().statistics.isEmpty())
        game = engine.reveal(game, game.cells.indexOfFirst { it.mine })
        val recorded = AppData(game = game).recordResult()
        assertEquals(1, recorded.statistics[Difficulty.EXPERT]?.losses)
        assertNull(recorded.statistics[Difficulty.EXPERT]?.bestMillis)
        assertEquals(recorded, roundTrip(recorded).recordResult())
    }
    @Test fun `custom wins have no combined best time`() {
        var game = engine.reveal(Game(BoardConfig(5, 5, 3)), 12)
        game.cells.indices.filter { !game.cells[it].mine }.forEach { game = engine.reveal(game, it) }
        val recorded = AppData(game = game).recordResult()
        assertEquals(1, recorded.statistics[Difficulty.CUSTOM]?.wins)
        assertNull(recorded.statistics[Difficulty.CUSTOM]?.bestMillis)
    }
    @Test fun `invalid data is treated as corruption`() = runTest {
        for (text in listOf("broken", "{\"schemaVersion\":99}", "{\"game\":{\"config\":{\"columns\":0,\"rows\":5,\"mines\":1}}}")) {
            try { AppDataSerializer.readFrom(ByteArrayInputStream(text.toByteArray())); fail("Expected corruption") }
            catch (_: CorruptionException) { /* expected */ }
        }
    }
    @Test fun `DataStore recovers corrupt file with a visible recovery state`() = runTest {
        val file = Files.createTempFile("minesweeper-corrupt", ".json").toFile()
        file.writeText("not a save")
        val job = SupervisorJob()
        val store = DataStoreFactory.create(AppDataSerializer, ReplaceFileCorruptionHandler { AppData(recovered = true) }, scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        try { assertTrue(store.data.first().recovered) }
        finally { job.cancelAndJoin(); file.delete() }
    }
    @Test fun `DataStore survives close and reopen without changing game`() = runTest {
        val folder = Files.createTempDirectory("minesweeper-store").toFile()
        val file = folder.resolve("state.json")
        val original = AppData(game = engine.reveal(Game(BoardConfig(9, 9, 10)), 40).copy(elapsedMillis = 19000))
        val job = SupervisorJob()
        val first = DataStoreFactory.create(AppDataSerializer, scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        first.updateData { original }
        job.cancelAndJoin()
        val nextJob = SupervisorJob()
        val next = DataStoreFactory.create(AppDataSerializer, scope = CoroutineScope(Dispatchers.IO + nextJob), produceFile = { file })
        try { assertEquals(original, next.data.first()) }
        finally { nextJob.cancelAndJoin(); file.delete(); folder.delete() }
    }
    @Test fun `failed partial write preserves cached and persisted progress`() = runTest {
        val directory = Files.createTempDirectory("minescape-write-failure").toFile()
        val file = directory.resolve("state.json")
        var failWrites = false
        val serializer = object : Serializer<AppData> by AppDataSerializer {
            override suspend fun writeTo(t: AppData, output: OutputStream) {
                if (failWrites) { output.write("partial".toByteArray()); throw IOException("Simulated full disk") }
                AppDataSerializer.writeTo(t, output)
            }
        }
        val job = SupervisorJob()
        val store = DataStoreFactory.create(serializer, scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        val original = AppData(game = engine.reveal(Game(BoardConfig(9, 9, 10)), 40))
        try {
            store.updateData { original }
            failWrites = true
            try { store.updateData { AppData() }; fail("Write should fail") } catch (_: IOException) { }
            assertEquals(original, store.data.first())
        } finally { job.cancelAndJoin() }
        try { assertEquals(original, file.inputStream().use { AppDataSerializer.readFrom(it) }) }
        finally { file.delete(); directory.delete() }
    }
}
