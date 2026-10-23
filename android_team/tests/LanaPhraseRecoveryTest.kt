package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaPhraseRecoveryTest {
    @Test fun repeatedLostContinuationsStopAfterTwoSpokenRetries() {
        val policy=LanaPhraseRecovery()
        assertTrue(policy.retryAllowed());assertTrue(policy.retryAllowed())
        assertFalse(policy.retryAllowed());assertFalse(policy.retryAllowed())
    }
    @Test fun completeQuestionResetsTheRetryBudget() {
        val policy=LanaPhraseRecovery()
        repeat(3) { policy.retryAllowed() };policy.accepted()
        assertTrue(policy.retryAllowed())
    }
}
