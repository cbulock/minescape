package com.camer.minesweeper.game

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class Difficulty(val title: String) { BEGINNER("Beginner"), INTERMEDIATE("Intermediate"), EXPERT("Expert"), CUSTOM("Custom") }

@Serializable
data class BoardConfig(val columns: Int, val rows: Int, val mines: Int, val difficulty: Difficulty = Difficulty.CUSTOM) {
    val size get() = columns * rows
    fun isValid(): Boolean = columns in 5..50 && rows in 5..50 && mines in 1..(size * 35 / 100) &&
        (difficulty == Difficulty.CUSTOM || this == preset(difficulty))
    companion object {
        fun preset(difficulty: Difficulty): BoardConfig = when (difficulty) {
            Difficulty.BEGINNER -> BoardConfig(9, 9, 10, difficulty)
            Difficulty.INTERMEDIATE -> BoardConfig(16, 16, 40, difficulty)
            Difficulty.EXPERT -> BoardConfig(30, 16, 99, difficulty)
            Difficulty.CUSTOM -> BoardConfig(12, 12, 24)
        }
    }
}

@Serializable
enum class GameStatus { READY, PLAYING, WON, LOST }

@Serializable enum class GenerationMode { CLASSIC, NO_GUESS }
@Serializable enum class PlayMode { NORMAL, PRACTICE, DAILY }
@Serializable data class UndoMove(val revealed: List<Int>, val flagged: List<Int>, val status: GameStatus, val explodedIndex: Int,
    val questioned: List<Int> = emptyList())

@Serializable
data class Cell(val mine: Boolean = false, val adjacent: Int = 0, val revealed: Boolean = false, val flagged: Boolean = false,
    val questioned: Boolean = false)

@Serializable
data class Viewport(val cellDp: Float = 0f, val centerColumn: Float = 0f, val centerRow: Float = 0f)

@Serializable
data class Game(
    val config: BoardConfig,
    val id: String = java.util.UUID.randomUUID().toString(),
    val cells: List<Cell> = List(config.size) { Cell() },
    val status: GameStatus = GameStatus.READY,
    val elapsedMillis: Long = 0,
    val explodedIndex: Int = -1,
    val resultRecorded: Boolean = false,
    val flagMode: Boolean = false,
    val viewport: Viewport = Viewport(centerColumn = config.columns / 2f, centerRow = config.rows / 2f),
    val generation: GenerationMode = GenerationMode.CLASSIC,
    val mode: PlayMode = PlayMode.NORMAL,
    val dailyDate: String? = null,
    val undo: List<UndoMove> = emptyList(),
    val hintsUsed: Int = 0,
    val undosUsed: Int = 0,
    val minesHit: Int = 0,
    val openingIndex: Int? = null,
    val openingCells: List<Int> = emptyList(),
) {
    val finished get() = status == GameStatus.WON || status == GameStatus.LOST
    val flags get() = cells.count { it.flagged }
    val cleared get() = cells.count { it.revealed && !it.mine }
}

class GameEngine(private val random: Random = Random.Default) {
    fun newGame(config: BoardConfig): Game {
        require(config.isValid())
        return Game(config)
    }

    fun act(game: Game, index: Int, action: CellAction, questionMarks: Boolean = false): Game {
        val next = when (action) {
            // An explicit reveal cannot turn into a chord after a queued earlier move.
            CellAction.REVEAL -> if (game.cells.getOrNull(index)?.revealed == true) game else reveal(game, index)
            CellAction.TOGGLE_FLAG -> toggleFlag(game, index, questionMarks)
            CellAction.CHORD -> chord(game, index)
        }
        if (next == game || game.mode != PlayMode.PRACTICE) return next
        if (game.status == GameStatus.READY && next.status != GameStatus.READY) return next.copy(undo = emptyList())
        val delta = UndoMove(game.cells.indices.filter { game.cells[it].revealed != next.cells[it].revealed },
            game.cells.indices.filter { game.cells[it].flagged != next.cells[it].flagged }, game.status, game.explodedIndex,
            game.cells.indices.filter { game.cells[it].questioned != next.cells[it].questioned })
        return next.copy(undo = (game.undo + delta).takeLast(100),
            minesHit = game.minesHit + if (next.status == GameStatus.LOST) 1 else 0)
    }

    fun undo(game: Game): Game {
        if (game.mode != PlayMode.PRACTICE || game.undo.isEmpty()) return game
        val previous = game.undo.last()
        val cells = game.cells.toMutableList()
        previous.revealed.forEach { cells[it] = cells[it].copy(revealed = !cells[it].revealed) }
        previous.flagged.forEach { cells[it] = cells[it].copy(flagged = !cells[it].flagged) }
        previous.questioned.forEach { cells[it] = cells[it].copy(questioned = !cells[it].questioned) }
        return game.copy(cells = cells, status = previous.status, explodedIndex = previous.explodedIndex,
            undo = game.undo.dropLast(1), undosUsed = game.undosUsed + 1, resultRecorded = false)
    }

    fun toggleFlag(game: Game, index: Int, questionMarks: Boolean = false): Game {
        if (game.finished || index !in game.cells.indices || game.cells[index].revealed) return game
        val cell = game.cells[index]
        val next = when {
            cell.flagged -> cell.copy(flagged = false, questioned = questionMarks)
            cell.questioned -> cell.copy(questioned = false)
            else -> cell.copy(flagged = true)
        }
        return game.copy(cells = game.cells.toMutableList().also { it[index] = next })
    }

    fun reveal(game: Game, index: Int): Game {
        if (game.finished || index !in game.cells.indices || game.cells[index].flagged) return game
        if (game.cells[index].revealed) return chord(game, index)
        val started = if (game.status == GameStatus.READY) generate(game, index) else game
        val next = uncover(started, listOf(index))
        return if (game.status == GameStatus.READY) next.copy(openingCells = next.cells.indices.filter { next.cells[it].revealed }) else next
    }

    /** Retain the layout and safe opening, with a new identity and no record eligibility. */
    fun retryAsPractice(game: Game): Game {
        require(game.finished)
        val clean = game.copy(id = java.util.UUID.randomUUID().toString(), mode = PlayMode.PRACTICE,
            status = GameStatus.PLAYING, cells = game.cells.map { it.copy(revealed = false, flagged = false, questioned = false) },
            elapsedMillis = 0, explodedIndex = -1, resultRecorded = false, flagMode = false,
            undo = emptyList(), hintsUsed = 0, undosUsed = 0, minesHit = 0)
        if (game.openingCells.isNotEmpty()) {
            val cells = clean.cells.mapIndexed { i, cell -> cell.copy(revealed = i in game.openingCells) }
            return clean.copy(cells = cells, status = if (cells.all { it.mine || it.revealed }) GameStatus.WON else GameStatus.PLAYING)
        }
        // Older saves did not retain their opening. Use a safe zero and explain this in the UI.
        val opening = game.openingIndex ?: game.cells.indexOfFirst { !it.mine && it.adjacent == 0 }
            .takeIf { it >= 0 } ?: game.cells.indexOfFirst { !it.mine }
        val next = reveal(clean, opening)
        return next.copy(openingIndex = opening, openingCells = next.cells.indices.filter { next.cells[it].revealed })
    }

    fun chord(game: Game, index: Int): Game {
        if (game.finished || index !in game.cells.indices) return game
        val cell = game.cells[index]
        if (!cell.revealed || cell.adjacent == 0) return game
        val neighbors = neighbors(game.config, index)
        if (neighbors.count { game.cells[it].flagged } != cell.adjacent) return game
        return uncover(game, neighbors.filter { !game.cells[it].flagged && !game.cells[it].revealed })
    }

    private fun generate(game: Game, first: Int): Game {
        val safe = (neighbors(game.config, first) + first).toSet()
        val mines = game.cells.indices.filter { it !in safe }.shuffled(random).take(game.config.mines).toSet()
        return game.copy(status = GameStatus.PLAYING, openingIndex = first, cells = game.cells.mapIndexed { i, cell ->
            cell.copy(mine = i in mines, adjacent = neighbors(game.config, i).count { it in mines })
        })
    }

    private fun uncover(game: Game, starts: List<Int>): Game {
        val cells = game.cells.toMutableList()
        val queue = ArrayDeque<Int>().apply { addAll(starts) }
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val cell = cells[index]
            if (cell.flagged || cell.revealed) continue
            cells[index] = cell.copy(revealed = true, questioned = false)
            if (cell.mine) return game.copy(cells = cells, status = GameStatus.LOST, explodedIndex = index)
            if (cell.adjacent == 0) queue.addAll(neighbors(game.config, index).filter { !cells[it].revealed && !cells[it].flagged })
        }
        val won = cells.all { it.mine || it.revealed }
        return game.copy(cells = cells, status = if (won) GameStatus.WON else game.status)
    }

    companion object {
        fun neighbors(config: BoardConfig, index: Int): List<Int> {
            val x = index % config.columns
            val y = index / config.columns
            return buildList {
                for (row in (y - 1).coerceAtLeast(0)..(y + 1).coerceAtMost(config.rows - 1)) {
                    for (col in (x - 1).coerceAtLeast(0)..(x + 1).coerceAtMost(config.columns - 1)) {
                        val neighbor = row * config.columns + col
                        if (neighbor != index) add(neighbor)
                    }
                }
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val seconds = millis / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
