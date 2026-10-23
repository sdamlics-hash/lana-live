package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaGreetingsTest {
    @Test fun wakeAcknowledgementIsAlwaysTheRequestedPhrase() {
        repeat(8) { assertEquals("Да, Сантик.",LanaGreetings.next()) }
        for (name in listOf("Лана", "LANA", "lana")) {
            assertEquals(LanaVoiceCommands.Action.WAKE,LanaVoiceCommands.choose(name,false))
        }
    }
    @Test fun samsungWakeVariantIsLocalButLongerQuestionIsNotSwallowed() {
        for (active in listOf(false,true)) {
            assertEquals(LanaVoiceCommands.Action.WAKE,LanaVoiceCommands.choose("Ладно, ты тут?",active))
            assertNotEquals(LanaVoiceCommands.Action.WAKE,LanaVoiceCommands.choose("Ладно ты тут расскажи про Ригу",active))
            assertEquals(if(active) LanaVoiceCommands.Action.STOP else LanaVoiceCommands.Action.IGNORE,
                LanaVoiceCommands.choose("Лана стоп",active))
        }
    }
}
