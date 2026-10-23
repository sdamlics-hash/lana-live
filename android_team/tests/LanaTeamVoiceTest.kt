package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaTeamVoiceTest {
    @Test fun onlyExplicitDispatchCreatesTasksAndPreservesTaskText() {
        assertEquals("Составь план проверки.", LanaTeamVoice.task("Лана, передай команде задание: Составь план проверки."))
        assertEquals("Сколько будет 2+2?", LanaTeamVoice.task("Отправь помощнику OpenAI: Сколько будет 2+2?"))
        for (q in listOf("Можешь передать задание команде?", "Что такое команда", "Расскажи про ChatGPT", "Передай команде", "Передай ChatGPT: открой файл")) assertNull(q, LanaTeamVoice.task(q))
    }
    @Test fun CapabilityRepliesDoNotClaimChatOrOtherAgentAccess() {
        assertTrue(LanaTeamVoice.localReply("Кто в твоей команде?")!!.contains("не подключены"))
        assertTrue(LanaTeamVoice.localReply("Ты подключена к ChatGPT?")!!.contains("не управляю"))
        assertTrue(LanaTeamVoice.localReply("Ты работаешь круглосуточно?")!!.contains("не подтверждены"))
        assertNull(LanaTeamVoice.localReply("Как написать приложение"))
        assertTrue(LanaTeamVoice.wantsLastReply("Прочитай последний ответ команды!"))
    }
}
