package com.camer.minesweeper.game

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.random.Random

data class Hint(val index: Int, val mine: Boolean, val explanation: String)

/** Reads only revealed clue values, revealed positions, dimensions and the public mine count. */
object LogicSolver {
    private data class Constraint(val squares: Set<Int>, val count: Int, val reason: String)
    fun hint(game: Game, keepGoing: () -> Boolean = { true }): Hint? {
        if (game.status != GameStatus.PLAYING) return null
        val hidden = game.cells.indices.filter { !game.cells[it].revealed }.toSet()
        val constraints = linkedMapOf<Set<Int>, Constraint>()
        fun add(squares: Set<Int>, count: Int, reason: String): Boolean {
            if (squares.isEmpty() || count !in 0..squares.size || constraints.containsKey(squares)) return false
            if (constraints.size >= 2048) return false
            constraints[squares] = Constraint(squares, count, reason.take(600)); return true
        }
        game.cells.forEachIndexed { i, c -> if (c.revealed) {
            add(GameEngine.neighbors(game.config, i).filter { it in hidden }.toSet(), c.adjacent,
                "The clue at row ${i / game.config.columns + 1}, column ${i % game.config.columns + 1} is ${c.adjacent}.")
        } }
        add(hidden, game.config.mines, "There are ${game.config.mines} mines in the whole board.")
        val knownMines = mutableSetOf<Int>()
        repeat(game.config.size * 2) {
            if (!keepGoing()) return null
            val snapshot = constraints.values.toList()
            var changed = false
            for (c in snapshot) {
                val remaining = c.squares - knownMines
                val count = c.count - c.squares.count { it in knownMines }
                if (remaining.isEmpty()) continue
                if (count == 0) {
                    val i = remaining.minOrNull()!!
                    return Hint(i, false, "${c.reason} After accounting for the proven mines, these ${remaining.size} covered squares contain no mines. This square is safe.")
                }
                if (count == remaining.size) {
                    val i = remaining.sorted().firstOrNull { !game.cells[it].flagged }
                    if (i != null) return Hint(i, true, "${c.reason} The remaining ${remaining.size} covered squares must all be mines. Flag this square.")
                    changed = knownMines.addAll(remaining) || changed
                }
                changed = add(remaining, count, c.reason) || changed
            }
            val reduced = constraints.values.toList()
            for (small in reduced) for (large in reduced) {
                if (!keepGoing()) return null
                if (large.squares.size > 16 || small.squares.size >= large.squares.size || !large.squares.containsAll(small.squares)) continue
                val difference = large.squares - small.squares
                changed = add(difference, large.count - small.count,
                    "Comparing deductions: ${large.reason} ${small.reason}") || changed
            }
            if (!changed) return null
        }
        return null
    }

    fun solves(initial: Game, keepGoing: () -> Boolean = { true }): Boolean {
        val engine = GameEngine()
        var game = initial.copy(cells = initial.cells.map { it.copy(flagged = false) })
        repeat(game.config.size * 2) {
            if (!keepGoing()) return false
            if (game.status == GameStatus.WON) return true
            val hint = hint(game, keepGoing) ?: return false
            game = if (hint.mine) engine.toggleFlag(game, hint.index) else engine.reveal(game, hint.index)
            if (game.status == GameStatus.LOST) return false
        }
        return game.status == GameStatus.WON
    }
}

object NoGuessGenerator {
    suspend fun generate(game: Game, opening: Int, seed: Int = Random.nextInt(), maxCandidates: Int = 500,
                         maxMillis: Long = 10_000): Game? {
        val context = currentCoroutineContext()
        val deadline = System.nanoTime() + maxMillis * 1_000_000
        val engine = GameEngine(Random(seed))
        val keepGoing = { context.ensureActive(); System.nanoTime() < deadline }
        repeat(maxCandidates) {
            if (!keepGoing()) return null
            val candidate = engine.reveal(game.copy(cells = game.cells.map { it.copy(flagged = false) }), opening)
            if (LogicSolver.solves(candidate, keepGoing)) {
                // Pre-opening flags are preserved; certification never assumes them correct.
                val opened = engine.reveal(candidate.copy(status = GameStatus.PLAYING, cells = candidate.cells.mapIndexed { i, cell ->
                    cell.copy(revealed = false, flagged = game.cells[i].flagged, questioned = game.cells[i].questioned)
                }, undo = emptyList()), opening)
                return opened.copy(openingCells = opened.cells.indices.filter { opened.cells[it].revealed })
            }
        }
        return null
    }
}
