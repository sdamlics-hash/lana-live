package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaIncompleteSpeechTest {
    @Test fun rejectsActualLostArithmeticPrefixes() {
        for (text in listOf("плюс пять", "Лана, плюс пять", "Ладно плюс двадцать восемь",
                "минус 4", "разделить на восемь", "умножить на 8")) {
            assertTrue(text, LanaIncompleteSpeech.missingFirstOperand(text))
        }
    }
    @Test fun neverRejectsCompleteOrUnrelatedQuestions() {
        for (text in listOf("Лана сколько будет 5 плюс 5?", "Сколько будет 12 + 8",
                "Лана, сколько будет двенадцать плюс восемь?", "плюс пять идей",
                "плюс пять минут", "а теперь плюс пять", "Привет, плюс пять",
                "Лана ты тут", "двадцать плюс восемь", "почему плюс пять")) {
            assertFalse(text, LanaIncompleteSpeech.missingFirstOperand(text))
        }
    }
}
