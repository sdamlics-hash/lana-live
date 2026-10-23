package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaRecognitionLeaseTest {
    @Test fun stoppedLeaseRejectsEveryLateCallback() {
        val lease = LanaRecognitionLease(); lease.attach("screen-A")
        val ticket = lease.begin("screen-A")!!
        assertTrue(lease.accepts("screen-A",ticket)); assertTrue(lease.cancel("screen-A"))
        assertFalse(lease.accepts("screen-A",ticket)); assertFalse(lease.finish("screen-A",ticket))
    }
    @Test fun oldScreenCannotStopNewOwnerOrConsumeItsSpeech() {
        val lease = LanaRecognitionLease(); lease.attach("old"); val old = lease.begin("old")!!
        lease.attach("new"); val current = lease.begin("new")!!
        assertFalse(lease.detach("old")); assertFalse(lease.cancel("old")); assertNull(lease.begin("old"))
        assertFalse(lease.accepts("old",old)); assertTrue(lease.accepts("new",current))
    }
    @Test fun eachFinalResultIsConsumedExactlyOnce() {
        val lease = LanaRecognitionLease(); lease.attach("A"); val one = lease.begin("A")!!
        assertTrue(lease.finish("A",one)); assertFalse(lease.finish("A",one))
        val two = lease.begin("A")!!; assertNotEquals(one,two)
        assertFalse(lease.accepts("A",one)); assertTrue(lease.accepts("A",two))
    }
    @Test fun detachAndReattachCannotReviveAnOldRetry() {
        val lease = LanaRecognitionLease(); lease.attach("A"); val old = lease.begin("A")!!
        lease.detach("A"); lease.attach("A"); val current = lease.begin("A")!!
        assertFalse(lease.accepts("A",old)); assertTrue(lease.accepts("A",current))
    }
}
