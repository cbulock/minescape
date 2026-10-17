package com.cbulock.minescape.data

import com.cbulock.minescape.game.*
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Instant

@Serializable enum class ThemeMode { SYSTEM, LIGHT, DARK }
@Serializable enum class ActiveSlot { REGULAR, DAILY }
@Serializable enum class DailyOutcome { IN_PROGRESS, WON, LOST, ABANDONED }
@Serializable data class Preferences(val theme: ThemeMode = ThemeMode.SYSTEM, val haptics: Boolean = true,
    val generation: GenerationMode = GenerationMode.CLASSIC, val practice: Boolean = false,
    val lastCustom: BoardConfig = BoardConfig.preset(Difficulty.CUSTOM),
    val questionMarks: Boolean = false, val holdMillis: Long? = null, val tapToChord: Boolean = true)
@Serializable data class Statistics(val wins: Int = 0, val losses: Int = 0, val bestMillis: Long? = null,
    val currentStreak: Int = 0, val bestStreak: Int = 0) {
    val winPercent get() = if (wins + losses == 0) null else wins * 100f / (wins + losses)
}
@Serializable data class GameResult(val id: String, val config: BoardConfig, val generation: GenerationMode,
    val won: Boolean, val elapsedMillis: Long, val finishedAt: String,
    val previousBestMillis: Long? = null, val newBest: Boolean = false)
@Serializable data class DailyRecord(val date: String, val outcome: DailyOutcome, val elapsedMillis: Long = 0)
@Serializable data class AppData(
    val schemaVersion: Int = 3, val game: Game? = null, val preferences: Preferences = Preferences(),
    val statistics: Map<Difficulty, Statistics> = emptyMap(), val recovered: Boolean = false,
    val dailyGame: Game? = null, val activeSlot: ActiveSlot = ActiveSlot.REGULAR,
    val noGuessStatistics: Map<Difficulty, Statistics> = emptyMap(), val results: List<GameResult> = emptyList(),
    val dailyRecords: Map<String, DailyRecord> = emptyMap(), val migrated: Boolean = false,
) {
    val activeGame get() = if (activeSlot == ActiveSlot.DAILY) dailyGame else game
    fun withActiveGame(value: Game?) = if (activeSlot == ActiveSlot.DAILY) copy(dailyGame = value) else copy(game = value)
}

fun AppData.migrate(): AppData {
    require(schemaVersion in 1..3) { "Unsupported save version" }
    return if (schemaVersion < 3) copy(schemaVersion = 3, migrated = true) else this
}

/** The terminal board and its score are saved in the same transaction. */
fun AppData.recordResult(now: String = Instant.now().toString()): AppData {
    var updated = this
    game?.let { session ->
        if (session.finished && !session.resultRecorded && session.mode == PlayMode.NORMAL) {
            val map = if (session.generation == GenerationMode.CLASSIC) statistics else noGuessStatistics
            val old = map[session.config.difficulty] ?: Statistics()
            val won = session.status == GameStatus.WON
            val streak = if (won) old.currentStreak + 1 else 0
            val stats = old.copy(wins = old.wins + if (won) 1 else 0, losses = old.losses + if (won) 0 else 1,
                currentStreak = streak, bestStreak = maxOf(old.bestStreak, streak),
                bestMillis = if (won && session.config.difficulty != Difficulty.CUSTOM)
                    minOf(old.bestMillis ?: Long.MAX_VALUE, session.elapsedMillis) else old.bestMillis)
            val eligible = won && session.config.difficulty != Difficulty.CUSTOM
            val result = GameResult(session.id, session.config, session.generation, won, session.elapsedMillis, now,
                previousBestMillis = if (eligible) old.bestMillis else null,
                newBest = eligible && (old.bestMillis == null || session.elapsedMillis < old.bestMillis))
            updated = updated.copy(game = session.copy(resultRecorded = true),
                results = (results.filterNot { it.id == session.id } + result).takeLast(100))
            updated = if (session.generation == GenerationMode.CLASSIC) updated.copy(statistics = map + (session.config.difficulty to stats))
                else updated.copy(noGuessStatistics = map + (session.config.difficulty to stats))
        }
    }
    dailyGame?.let { session ->
        if (!session.finished && session.dailyDate != null) updated = updated.copy(dailyRecords = updated.dailyRecords +
            (session.dailyDate to DailyRecord(session.dailyDate, DailyOutcome.IN_PROGRESS, session.elapsedMillis)))
        if (session.finished && !session.resultRecorded) {
            val date = requireNotNull(session.dailyDate)
            updated = updated.copy(dailyGame = session.copy(resultRecorded = true), dailyRecords = updated.dailyRecords +
                (date to DailyRecord(date, if (session.status == GameStatus.WON) DailyOutcome.WON else DailyOutcome.LOST, session.elapsedMillis)))
        }
    }
    return updated
}

private fun validateGame(g: Game) {
    require(g.config.isValid() && g.cells.size == g.config.size && g.elapsedMillis >= 0)
    require(g.viewport.cellDp.isFinite() && g.viewport.cellDp in 0f..80f && g.viewport.centerColumn.isFinite() && g.viewport.centerRow.isFinite())
    require(g.hintsUsed >= 0 && g.undosUsed >= 0 && g.minesHit >= 0 && g.undo.size <= 100)
    require(g.generation != GenerationMode.NO_GUESS || g.config.difficulty != Difficulty.CUSTOM)
    require(g.mode == PlayMode.PRACTICE || g.undo.isEmpty())
    require(g.cells.none { (it.revealed && (it.flagged || it.questioned)) || (it.flagged && it.questioned) })
    require(g.openingCells.distinct().size == g.openingCells.size && g.openingCells.all { it in g.cells.indices && !g.cells[it].mine })
    require(g.openingIndex == null || g.openingIndex in g.cells.indices && !g.cells[g.openingIndex].mine)
    if (g.status == GameStatus.READY) {
        require(g.cells.none { it.mine || it.revealed || it.adjacent != 0 } && g.elapsedMillis == 0L && g.openingCells.isEmpty() && g.openingIndex == null)
    } else {
        require(g.cells.count { it.mine } == g.config.mines)
        g.cells.forEachIndexed { i, cell -> require(cell.adjacent == GameEngine.neighbors(g.config, i).count { g.cells[it].mine }) }
    }
    require(!g.resultRecorded || g.finished)
    if (g.status == GameStatus.WON) require(g.cells.all { it.mine || it.revealed })
    if (g.status == GameStatus.LOST) require(g.explodedIndex in g.cells.indices && g.cells[g.explodedIndex].mine && g.cells[g.explodedIndex].revealed)
    if (!g.finished) require(g.cells.none { it.mine && it.revealed })
    g.dailyDate?.let { LocalDate.parse(it) }
    if (g.mode == PlayMode.DAILY) require(g.dailyDate != null && g.config == BoardConfig.preset(Difficulty.INTERMEDIATE) && g.generation == GenerationMode.NO_GUESS)
    var prior = g.copy(undo = emptyList())
    for (move in g.undo.asReversed()) {
        require(move.revealed.distinct().size == move.revealed.size && move.flagged.distinct().size == move.flagged.size && move.questioned.distinct().size == move.questioned.size)
        require((move.revealed + move.flagged + move.questioned).all { it in g.cells.indices })
        val cells = prior.cells.toMutableList()
        move.revealed.forEach { cells[it] = cells[it].copy(revealed = !cells[it].revealed) }
        move.flagged.forEach { cells[it] = cells[it].copy(flagged = !cells[it].flagged) }
        move.questioned.forEach { cells[it] = cells[it].copy(questioned = !cells[it].questioned) }
        prior = prior.copy(cells = cells, status = move.status, explodedIndex = move.explodedIndex, resultRecorded = false)
        validateGame(prior)
    }
}

fun AppData.validate(): AppData {
    require(schemaVersion == 3) { "Unsupported save version" }
    game?.let { require(it.mode != PlayMode.DAILY); validateGame(it) }
    dailyGame?.let { require(it.mode == PlayMode.DAILY); validateGame(it) }
    require(activeSlot != ActiveSlot.DAILY || dailyGame != null)
    require(preferences.lastCustom.isValid() && preferences.lastCustom.difficulty == Difficulty.CUSTOM)
    require(preferences.holdMillis == null || preferences.holdMillis in 200L..1500L)
    require((statistics.values + noGuessStatistics.values).all {
        it.wins >= 0 && it.losses >= 0 && it.wins.toLong() + it.losses <= Int.MAX_VALUE &&
            it.currentStreak >= 0 && it.bestStreak >= it.currentStreak && it.bestStreak <= it.wins && (it.bestMillis == null || it.bestMillis >= 0)
    })
    require(results.size <= 100 && results.map { it.id }.distinct().size == results.size)
    require(Difficulty.CUSTOM !in noGuessStatistics)
    results.forEach {
        require(it.config.isValid() && it.elapsedMillis >= 0 && (it.generation != GenerationMode.NO_GUESS || it.config.difficulty != Difficulty.CUSTOM))
        Instant.parse(it.finishedAt)
        require(it.previousBestMillis == null || it.previousBestMillis >= 0)
        require(!it.newBest || it.won && it.config.difficulty != Difficulty.CUSTOM &&
            (it.previousBestMillis == null || it.elapsedMillis < it.previousBestMillis))
    }
    dailyRecords.forEach { (key, record) -> LocalDate.parse(key); require(key == record.date && record.elapsedMillis >= 0) }
    dailyGame?.let { g ->
        val record = dailyRecords[g.dailyDate]
        require(record != null)
        require(if (!g.finished) record.outcome == DailyOutcome.IN_PROGRESS else record.outcome ==
            if (g.status == GameStatus.WON) DailyOutcome.WON else DailyOutcome.LOST)
    }
    require(dailyRecords.values.filter { it.outcome == DailyOutcome.IN_PROGRESS }.all { it.date == dailyGame?.dailyDate })
    return this
}
