package com.camer.minesweeper.game

enum class CellAction { REVEAL, TOGGLE_FLAG, CHORD }
data class CellIntent(val gameId: String, val index: Int, val action: CellAction, val flagMode: Boolean, val fromTap: Boolean = false)

/** One contact sequence owns one target and can emit at most one cell action. */
class PressGesture(
    private val target: Int?, private val revealed: Boolean, private val flagMode: Boolean,
    private val timeout: Long, private val slop: Float, private val tapToChord: Boolean = true,
) {
    enum class State { PENDING, FLAGGED, TRANSFORMING, CANCELLED }
    var state = if (target == null) State.CANCELLED else State.PENDING
        private set
    val pendingTarget get() = target.takeIf { state == State.PENDING }
    fun cancel() { if (state != State.FLAGGED) state = State.CANCELLED }
    fun movement(hit: Int?, distance: Float, multiple: Boolean, consumed: Boolean) {
        if (state == State.FLAGGED) return
        if (consumed) { cancel(); return }
        if (multiple || distance > slop) { state = State.TRANSFORMING; return }
        if (hit != target) cancel()
    }
    fun hold(): CellAction? {
        if (state != State.PENDING) return null
        state = State.FLAGGED
        return if (revealed) null else CellAction.TOGGLE_FLAG
    }
    fun release(elapsed: Long): CellAction? {
        if (state != State.PENDING) return null
        if (elapsed >= timeout) return hold()
        state = State.CANCELLED
        return when { revealed -> if (tapToChord) CellAction.CHORD else null; flagMode -> CellAction.TOGGLE_FLAG; else -> CellAction.REVEAL }
    }
}
