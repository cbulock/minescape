package com.cbulock.minescape.game

import org.junit.Assert.*
import org.junit.Test

class PressGestureTest {
    @Test fun `custom thresholds preserve one action and disabled tap chord never reveals`() {
        for (duration in listOf(200L, 300L, 800L, 1500L)) {
            assertEquals(CellAction.REVEAL, PressGesture(7, false, false, duration, 10f).release(duration - 1))
            val hold = PressGesture(7, false, false, duration, 10f)
            assertEquals(CellAction.TOGGLE_FLAG, hold.release(duration)); assertNull(hold.release(duration + 1))
            assertNull(PressGesture(7, true, false, duration, 10f, false).release(100))
            assertNull(PressGesture(7, true, false, duration, 10f, false).release(duration))
        }
    }
    private fun press(revealed: Boolean = false, flag: Boolean = false) = PressGesture(7, revealed, flag, 500, 10f)
    @Test fun `release boundary cannot downgrade a hold to a tap`() {
        assertEquals(CellAction.REVEAL, press().release(499))
        for (duration in listOf(500L, 501L, 5000L)) {
            val p = press()
            assertEquals(CellAction.TOGGLE_FLAG, p.release(duration))
            assertNull(p.release(duration)); assertNull(p.hold())
        }
    }
    @Test fun `holding revealed cells never chords`() {
        assertNull(press(true).hold()); assertNull(press(true).release(501))
        assertEquals(CellAction.CHORD, press(true).release(100))
    }
    @Test fun `jitter stays anchored but crossing then returning cancels`() {
        val jitter = press(); jitter.movement(7, 3f, false, false)
        assertEquals(CellAction.REVEAL, jitter.release(100))
        val crossed = press(); crossed.movement(8, 2f, false, false); crossed.movement(7, 0f, false, false)
        assertNull(crossed.release(100)); assertNull(crossed.hold())
    }
    @Test fun `drag pinch consumption and cancellation never activate`() {
        val drag = press(); drag.movement(7, 11f, false, false)
        val pinch = press(); pinch.movement(7, 0f, true, false)
        val consumed = press(); consumed.movement(7, 0f, false, true)
        val cancel = press(); cancel.cancel()
        for (p in listOf(drag, pinch, consumed, cancel)) { assertNull(p.release(900)); assertNull(p.hold()) }
    }
    @Test fun `hold locks subsequent movement and release`() {
        val p = press(); assertEquals(CellAction.TOGGLE_FLAG, p.hold())
        p.movement(8, 100f, true, false); assertEquals(PressGesture.State.FLAGGED, p.state)
        assertNull(p.release(1000))
        assertEquals(CellAction.TOGGLE_FLAG, press(flag = true).release(100))
    }
}
