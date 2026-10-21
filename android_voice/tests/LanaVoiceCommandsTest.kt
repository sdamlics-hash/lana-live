package ai.dreibus.lanalive

import org.junit.Assert.assertEquals
import org.junit.Test

class LanaVoiceCommandsTest {
    private fun check(input: String, active: Boolean, expected: LanaVoiceCommands.Action) {
        assertEquals("voice command: $input, active=$active", expected,
            LanaVoiceCommands.choose(input, active))
    }

    @Test fun wakePhraseStartsConversation() {
        check("Лана, ты тут?", false, LanaVoiceCommands.Action.WAKE)
        check("Лана, привет!", false, LanaVoiceCommands.Action.WAKE)
        check("Лана, ты здесь?", false, LanaVoiceCommands.Action.WAKE)
        check("Лана", false, LanaVoiceCommands.Action.WAKE)
    }

    @Test fun followUpNeedsActiveConversation() {
        check("А завтра что будет?", true, LanaVoiceCommands.Action.ASK)
        check("А завтра что будет?", false, LanaVoiceCommands.Action.IGNORE)
        check("Расскажи о золоте, Лана", false, LanaVoiceCommands.Action.ASK)
    }

    @Test fun stoppingHasPriorityOverWakeWord() {
        check("Лана, хватит", true, LanaVoiceCommands.Action.STOP)
        check("Лана, стоп!", true, LanaVoiceCommands.Action.STOP)
        check("Стоп разговор", true, LanaVoiceCommands.Action.STOP)
        check("Лана, замолчи", true, LanaVoiceCommands.Action.STOP)
        check("Пока, Лана", true, LanaVoiceCommands.Action.STOP)
    }

    @Test fun irrelevantSpeechCannotInterruptSleepMode() {
        check("", false, LanaVoiceCommands.Action.IGNORE)
        check("Стоп разговор", false, LanaVoiceCommands.Action.IGNORE)
        check("Смотри телевизор", false, LanaVoiceCommands.Action.IGNORE)
    }

    @Test fun normalizationIsStable() {
        check("   ЛАНА!!!    Ты   ТУТ??  ", false, LanaVoiceCommands.Action.WAKE)
        check("Лана, всё!", true, LanaVoiceCommands.Action.STOP)
        check("   Лана, ХВАТИТ! ", true, LanaVoiceCommands.Action.STOP)
    }
}
