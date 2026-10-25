package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaSpeechAssemblyTest {
    @Test fun nameAndQuestionAreOneUtterance() {
        val a = LanaSpeechAssembly()
        a.segment("Лана",0)
        assertFalse(a.ready(1399))
        a.speechBegan()
        assertFalse(a.ready(5000))
        a.segment("предложи три улучшения",6000)
        assertTrue(a.ready(7400))
        assertEquals("Лана предложи три улучшения",a.take())
        assertFalse(a.hasText)
    }
    @Test fun finalInstructionAfterPauseIsPreservedVerbatim() {
        val a = LanaSpeechAssembly()
        a.segment("Стиль уже сохранён или только в текущем разговоре?",100)
        a.speechBegan()
        a.segment("Если сохранить настройку не можешь, скажи прямо.",900)
        assertFalse(a.ready(2299))
        assertTrue(a.ready(2300))
        assertTrue(a.take().endsWith("Если сохранить настройку не можешь, скажи прямо."))
    }
    @Test fun cancellationDropsPendingTextAndDeadline() {
        val a=LanaSpeechAssembly(); a.segment("Лана",0); a.clear()
        assertFalse(a.ready(10000)); assertFalse(a.continuing); assertNull(a.remaining(10000))
    }
    @Test fun failedContinuationCannotBeTreatedAsCompletedQuestion() {
        val a=LanaSpeechAssembly(); a.segment("Вопрос",0); a.speechBegan()
        assertTrue(a.hasText); assertTrue(a.continuing); assertFalse(a.ready(10000))
    }
    @Test fun overlappingFinalsDoNotDuplicateWakeWordsOrArithmeticOperands() {
        val a = LanaSpeechAssembly()
        assertTrue(a.segment("Лана", 0))
        assertTrue(a.segment("Лана, сколько будет", 180))
        assertTrue(a.segment("сколько будет двенадцать плюс восемь", 360))
        assertFalse(a.ready(1759))
        assertTrue(a.ready(1760))
        assertEquals("Лана, сколько будет двенадцать плюс восемь",a.take())
        assertFalse(a.hasText)
    }
    @Test fun repeatedAndShorterLateFinalsAreIdempotent() {
        val a = LanaSpeechAssembly()
        a.segment("Расскажи про подключение",0)
        a.segment("Расскажи про подключение",100)
        a.segment("Расскажи",200)
        assertEquals("Расскажи про подключение",a.take())
    }
    @Test fun distinctNextSentenceAndLegitimateSingleWordRepetitionStayIntact() {
        val a = LanaSpeechAssembly()
        a.segment("Да",0)
        a.segment("да, давай продолжим",100)
        a.segment("Это новая тема",200)
        assertEquals("да, давай продолжим Это новая тема",a.take())
        val b=LanaSpeechAssembly()
        b.segment("Я хочу",0)
        b.segment("хочу продолжить",30)
        assertEquals("Я хочу хочу продолжить",b.take())
    }
    @Test fun foldedFinalStillHonorsTotalSizeAndFailsAtomically() {
        val a=LanaSpeechAssembly()
        a.segment("а".repeat(7999),0)
        assertFalse(a.segment("ещё слова",1))
        assertEquals("а".repeat(7999),a.take())
    }
    @Test fun oversizeRejectsWithoutTruncatingStoredSegments() {
        val a=LanaSpeechAssembly(); assertTrue(a.segment("я".repeat(4000),0))
        assertFalse(a.segment("ю".repeat(4001),1)); assertEquals(4000,a.take().length)
    }
}
