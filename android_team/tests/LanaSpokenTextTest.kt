package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaSpokenTextTest {
    @Test fun arithmeticEmojiKeepTheirMeaningInSpeech() {
        assertEquals("17 − 25 = −8",LanaSpokenText.clean("17 ➖ 25 = −8"))
        assertEquals("2 + 3 × 4 ÷ 2",LanaSpokenText.clean("2 ➕ 3 ✖️ 4 ➗ 2"))
    }
    @Test fun winkIsNotSentToSpeechSynthesis() {
        assertEquals("Коротко и по делу!",LanaSpokenText.clean("Коротко и по делу 😉!"))
        assertEquals("Спасибо.",LanaSpokenText.clean("Спасибо ❤️."))
    }
    @Test fun amountsMathAndActualWordsArePreserved() {
        val text="17 + 25 = 42. Стоимость 480 €; 10 кг. Подмигивание — название жеста."
        assertEquals(text,LanaSpokenText.clean(text))
    }
    @Test fun multiCodepointEmojiAndEmojiOnlyRepliesAreHandled() {
        assertEquals("Готово",LanaSpokenText.clean("👩‍💻 Готово 👍🏽"))
        assertEquals("",LanaSpokenText.clean("😉 ❤️"))
        assertEquals("1. План",LanaSpokenText.clean("1️⃣. План"))
    }
}
