package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaRecognitionTurnTest {
    @Test fun finalResultCanOnlyBeConsumedOnce() {
        val t=LanaRecognitionTurn()
        assertTrue(t.finish()); assertFalse(t.finish()); assertFalse(t.acceptsEvents())
        assertFalse(t.endOfSpeech(0)); assertFalse(t.timeout(10000))
    }
    @Test fun endOfSpeechDoesNotSendPartialOrCompleteQuestion() {
        val t=LanaRecognitionTurn()
        assertTrue(t.endOfSpeech(100)); assertFalse(t.endOfSpeech(200))
        assertTrue(t.acceptsEvents()); assertFalse(t.timeout(1400))
        assertTrue(t.finish()); assertFalse(t.timeout(7000))
    }
    @Test fun shortPauseAndResumedSpeechCancelOldDeadline() {
        val t=LanaRecognitionTurn()
        t.endOfSpeech(100); t.speechBegan()
        assertNull(t.endedAt); assertFalse(t.timeout(10000)); assertTrue(t.acceptsEvents())
        t.endOfSpeech(11000)
        assertFalse(t.timeout(17499)); assertTrue(t.timeout(17500)); assertFalse(t.timeout(18000))
    }
    @Test fun timeoutClosesTurnAndLateFinalCannotTriggerDuplicateCall() {
        val t=LanaRecognitionTurn()
        t.endOfSpeech(1000)
        assertFalse(t.timeout(7499)); assertTrue(t.timeout(7500))
        assertFalse(t.finish()); assertFalse(t.acceptsEvents())
    }
    @Test fun freshRecognizerHasItsOwnIndependentTurn() {
        val old=LanaRecognitionTurn(); old.finish()
        val fresh=LanaRecognitionTurn()
        assertFalse(old.finish()); assertTrue(fresh.finish())
    }
    @Test fun targetIsWithinRequestedRangeAndDoesNotSetAPaidTimer() {
        assertTrue(LanaRecognitionTurn.TARGET_SILENCE_MS in 1000L..1500L)
        assertTrue(LanaRecognitionTurn.INCOMPLETE_SILENCE_MS >= LanaRecognitionTurn.TARGET_SILENCE_MS)
        val t=LanaRecognitionTurn()
        assertFalse(t.timeout(100000)); assertTrue(t.acceptsEvents())
    }
}
