package com.camer.minesweeper.game

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class DailyChallenge(private val layouts: List<List<Int>>, private val clock: Clock = Clock.systemUTC()) {
    init { require(layouts.size == 366 && layouts.all { it.size == 40 && it.distinct().size == 40 && it.all { index -> index in 0..255 } }) }
    fun today(): String = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString()
    fun game(date: String, practice: Boolean = false): Game {
        val day = LocalDate.parse(date).toEpochDay()
        val transform = Math.floorMod(Math.floorDiv(day, 366), 8).toInt()
        fun transform(index: Int): Int {
            var x = index % 16; var y = index / 16
            if (transform >= 4) x = 15 - x
            repeat(transform % 4) { val previous = x; x = 15 - y; y = previous }
            return y * 16 + x
        }
        val mines = layouts[Math.floorMod(day, 366).toInt()].map(::transform).toSet()
        val config = BoardConfig.preset(Difficulty.INTERMEDIATE)
        val game = Game(config, cells = List(256) { i -> Cell(mine = i in mines,
            adjacent = GameEngine.neighbors(config, i).count { it in mines }) }, status = GameStatus.PLAYING,
            generation = GenerationMode.NO_GUESS, mode = if (practice) PlayMode.PRACTICE else PlayMode.DAILY, dailyDate = date)
        val opened = GameEngine().reveal(game, transform(119))
        return opened.copy(openingIndex = transform(119), openingCells = opened.cells.indices.filter { opened.cells[it].revealed })
    }
}
