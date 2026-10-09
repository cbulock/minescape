package com.camer.minesweeper.game

/** A separate, deterministic lesson. It never enters a saved game slot or scoring. */
object Tutorial {
    val targets = listOf(6, 0, 6)
    val actions = listOf(CellAction.REVEAL, CellAction.TOGGLE_FLAG, CellAction.CHORD)
    fun game(step: Int): Game {
        require(step in 0..3)
        val config = BoardConfig(5, 5, 1)
        var game = Game(config, id = "tutorial-$step", status = GameStatus.PLAYING,
            mode = PlayMode.PRACTICE, cells = List(25) { i -> Cell(mine = i == 0,
                adjacent = GameEngine.neighbors(config, i).count { it == 0 }) })
        val engine = GameEngine()
        repeat(step) { game = engine.act(game, targets[it], actions[it]) }
        return game
    }
    fun advance(step: Int, intent: CellIntent): Int = if (step in 0..2 &&
        intent.gameId == "tutorial-$step" && intent.index == targets[step] && intent.action == actions[step]) step + 1 else step
}
