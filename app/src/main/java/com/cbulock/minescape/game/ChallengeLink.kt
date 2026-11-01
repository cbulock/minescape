package com.cbulock.minescape.game

import java.net.URI
import java.nio.ByteBuffer
import java.time.LocalDate
import java.util.Base64
import java.util.zip.CRC32

sealed interface SharedChallenge {
    data class Daily(val date: String) : SharedChallenge
    data class Board(val config: BoardConfig, val mines: Set<Int>, val opening: Set<Int>, val openingIndex: Int) : SharedChallenge {
        fun game(): Game {
            val cells = List(config.size) { i -> Cell(mine = i in mines, revealed = i in opening,
                adjacent = GameEngine.neighbors(config, i).count { it in mines }) }
            return Game(config, cells = cells, mode = PlayMode.PRACTICE,
                status = if (cells.all { it.mine || it.revealed }) GameStatus.WON else GameStatus.PLAYING,
                openingIndex = openingIndex, openingCells = opening.sorted())
        }
    }
}

object ChallengeLink {
    const val BASE = "https://cbulock.github.io/minescape/play/"
    const val MAX_LENGTH = 1400
    fun daily(date: String): String = BASE + "#d1." + LocalDate.parse(date)
    fun encode(game: Game): String {
        require(game.status != GameStatus.READY) { "Reveal a square before sharing this board." }
        game.dailyDate?.let { return daily(it) }
        val clean = GameEngine().retryAsPractice(game.copy(status = GameStatus.LOST))
        val size = (game.config.size + 7) / 8
        val bytes = ByteBuffer.allocate(8 + size * 2 + 4)
        bytes.put(game.config.columns.toByte()).put(game.config.rows.toByte())
        bytes.putShort(game.config.mines.toShort()).putShort((clean.openingIndex ?: clean.openingCells.first()).toShort())
        bytes.putShort(size.toShort())
        for (indices in listOf(game.cells.indices.filter { game.cells[it].mine }.toSet(), clean.openingCells.toSet())) {
            val bitmap = ByteArray(size)
            indices.forEach { bitmap[it / 8] = (bitmap[it / 8].toInt() or (1 shl (it % 8))).toByte() }
            bytes.put(bitmap)
        }
        bytes.putInt(CRC32().apply { update(bytes.array(), 0, bytes.position()) }.value.toInt())
        return BASE + "#b1." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.array())
    }
    fun parse(text: String, today: LocalDate = LocalDate.now()): SharedChallenge {
        try {
            val value = text.trim()
            require(value.length <= MAX_LENGTH)
            val uri = URI(value)
            require(uri.rawQuery == null && uri.userInfo == null && uri.port == -1)
            require((uri.scheme == "https" && uri.host == "cbulock.github.io" && uri.path == "/minescape/play/") ||
                (uri.scheme == "minescape" && uri.host == "play" && uri.path == "/"))
            val fragment = requireNotNull(uri.rawFragment)
            if (fragment.startsWith("d1.")) {
                val date = fragment.removePrefix("d1.")
                require(date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
                val parsed = LocalDate.parse(date)
                require(!parsed.isAfter(today)) { "This daily puzzle is in the future." }
                return SharedChallenge.Daily(date)
            }
            require(fragment.startsWith("b1.")) { "This challenge version is not supported." }
            val payload = fragment.removePrefix("b1.")
            require(payload.matches(Regex("[A-Za-z0-9_-]+")))
            val raw = Base64.getUrlDecoder().decode(payload)
            require(raw.size in 20..638)
            val bytes = ByteBuffer.wrap(raw)
            val columns = bytes.get().toInt() and 255
            val rows = bytes.get().toInt() and 255
            val mines = bytes.short.toInt() and 65535
            val openingIndex = bytes.short.toInt() and 65535
            val config = listOf(Difficulty.BEGINNER, Difficulty.INTERMEDIATE, Difficulty.EXPERT)
                .map(BoardConfig::preset).find { it.columns == columns && it.rows == rows && it.mines == mines }
                ?: BoardConfig(columns, rows, mines)
            require(config.isValid() && openingIndex in 0 until config.size)
            val size = bytes.short.toInt() and 65535
            require(size == (config.size + 7) / 8 && raw.size == 12 + 2 * size)
            require(ByteBuffer.wrap(raw, raw.size - 4, 4).int == CRC32().apply { update(raw, 0, raw.size - 4) }.value.toInt())
            fun bitmap(): Set<Int> {
                val map = ByteArray(size).also(bytes::get)
                require((config.size until size * 8).none { map[it / 8].toInt() and (1 shl (it % 8)) != 0 })
                return (0 until config.size).filter { map[it / 8].toInt() and (1 shl (it % 8)) != 0 }.toSet()
            }
            val mineCells = bitmap()
            val opening = bitmap()
            require(mineCells.size == mines && opening.isNotEmpty() && openingIndex !in mineCells)
            require(opening.none { it in mineCells })
            return SharedChallenge.Board(config, mineCells, opening, openingIndex)
        } catch (e: Exception) {
            throw IllegalArgumentException("This challenge link is invalid, from the future, or uses an unsupported version.", e)
        }
    }
}
