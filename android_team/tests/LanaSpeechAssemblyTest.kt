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
        assertTrue(a.ready(6900))
        assertEquals("Лана предложи три улучшения",a.take())
        assertFalse(a.hasText)
    }
    @Test fun finalInstructionAfterPauseIsPreservedVerbatim() {
        val a = LanaSpeechAssembly()
        a.segment("Стиль уже сохранён или только в текущем разговоре?",100)
        a.speechBegan()
        a.segment("Если сохранить настройку не можешь, скажи прямо.",900)
        assertFalse(a.ready(1799))
        assertTrue(a.ready(1800))
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
        assertFalse(a.ready(1259))
        assertTrue(a.ready(1260))
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
    @Test fun q200RecognitionTimingAndContinuation() {
        var checked = 0
        for (index in 0 until 40) {
            val start = 1_000L + 73L * index
            val suffix = listOf("", "?", "!", ".", "…")[index % 5]
            val wake = listOf("Ты тут", "Лана, ты тут", "Ты меня слышишь", "Ты здесь, Лана")[index % 4]
            assertTrue(LanaSpeechAssembly.immediate(wake + suffix))
            checked++
            assertFalse(LanaSpeechAssembly.immediate("$wake и можешь продолжить дальше$suffix"))
            checked++
            val normal = LanaSpeechAssembly()
            normal.segment("Расскажи про Ригу$suffix", start)
            assertFalse(normal.ready(start + 899))
            assertTrue(normal.ready(start + 900))
            checked++
            val split = LanaSpeechAssembly()
            split.segment("Двенадцать плюс", start)
            assertFalse(split.ready(start + 1399))
            split.speechBegan()
            assertFalse(split.ready(start + 1500))
            split.segment("восемь$suffix", start + 1600)
            assertTrue(split.ready(start + 2500))
            assertEquals("Двенадцать плюс восемь$suffix", split.take())
            checked++
            val cancelled = LanaSpeechAssembly()
            cancelled.segment("Расскажи про погоду$suffix", start)
            cancelled.clear()
            assertFalse(cancelled.ready(start + 2000))
            assertFalse(cancelled.hasText)
            checked++
        }
        assertEquals(200, checked)
    }
}
