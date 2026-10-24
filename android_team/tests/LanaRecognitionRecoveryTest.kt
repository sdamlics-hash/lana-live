package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaRecognitionRecoveryTest {
    @Test fun silenceDoesNotBecomeAnErrorOrGrowBackoff() {
        val policy = LanaRecognitionRecovery()
        assertTrue(policy.error(7).diagnostic)
        repeat(100) { val d = policy.error(6); assertEquals(900L, d.delayMs); assertFalse(d.stop); assertFalse(d.diagnostic) }
        assertEquals(1000L, policy.error(2).delayMs)
    }
    @Test fun networkFailureIsBoundedAndSuccessResets() {
        val policy = LanaRecognitionRecovery()
        assertEquals(listOf(1000L,2000L,4000L,8000L,16000L,30000L,30000L), (1..7).map { policy.error(2).delayMs })
        policy.reset(); assertEquals(1000L, policy.error(2).delayMs)
    }
    @Test fun permissionsAndMissingLanguageStopInsteadOfLooping() {
        for (code in listOf(9,12,13)) assertTrue(LanaRecognitionRecovery().error(code).stop)
        for (code in listOf(3,8,11)) assertTrue(LanaRecognitionRecovery().error(code).recreate)
        assertEquals(30000L, LanaRecognitionRecovery().error(10).delayMs)
    }
    @Test fun repeatedNetworkLossRecreatesRecognizerAndSuccessfulPhraseClearsFailureStreak() {
        for (code in listOf(1,2)) {
            val policy = LanaRecognitionRecovery()
            assertFalse(policy.error(code).recreate)
            repeat(12) {
                val decision = policy.error(code)
                assertTrue(decision.recreate)
                assertFalse(decision.stop)
                assertTrue(decision.delayMs <= 30000L)
            }
            policy.successfulPhrase()
            assertFalse(policy.error(code).recreate)
            assertEquals(2000L, policy.error(code).delayMs)
        }
    }
}
