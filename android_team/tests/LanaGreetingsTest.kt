package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaGreetingsTest {
    @Test fun repliesVaryWithoutImmediateRepetitionAndRepeatOnlyAfterFullCycle() {
        val replies = (1..8).map { LanaGreetings.next() }
        assertEquals(4,replies.take(4).distinct().size)
        assertEquals(replies.take(4),replies.drop(4))
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
