package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaHandsFreeGateTest {
    @Test fun repeatsAfterEveryAcceptedTurn() {
        val gate = LanaHandsFreeGate(); gate.start()
        repeat(100) {
            val turn = gate.schedule()!!
            assertTrue(gate.begin(turn)); assertTrue(gate.accept(turn)); assertFalse(gate.accept(turn))
        }
    }
    @Test fun stopRejectsQueuedListeningAndLateFinal() {
        val gate = LanaHandsFreeGate(); gate.start()
        val queued = gate.schedule()!!; gate.stop()
        assertFalse(gate.begin(queued)); assertNull(gate.schedule())
        gate.start(); val current = gate.schedule()!!; assertTrue(gate.begin(current))
        gate.stop(); assertFalse(gate.accept(current))
    }
    @Test fun newestScheduleWinsAndOldSessionCannotDispatch() {
        val gate = LanaHandsFreeGate(); gate.start()
        val old = gate.schedule()!!; val newest = gate.schedule()!!
        assertFalse(gate.begin(old)); assertTrue(gate.begin(newest)); assertFalse(gate.begin(newest))
        gate.start(); assertFalse(gate.accept(newest))
    }
}
