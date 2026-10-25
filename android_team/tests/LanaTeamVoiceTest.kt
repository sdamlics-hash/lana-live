package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaTeamVoiceTest {
    @Test fun observedRecognitionVariantDispatchesButIncompleteCommandDoesNot() {
        assertEquals("предложить три способа сократить задержки голосового ответа", LanaTeamVoice.task("передай команды задание предложить три способа сократить задержки голосового ответа"))
        assertNull(LanaTeamVoice.task("Передай команде задание"))
        assertNull(LanaTeamVoice.task("Передай команде задание:"))
        assertNull(LanaTeamVoice.task("Передай командеврагов секрет"))
        assertTrue(LanaTeamVoice.localReply("Передай команде задание")!!.contains("ничего не отправлено"))
        assertNull(LanaTeamVoice.localReply("Передай команде задание: проверь план"))
    }

    @Test fun onlyExplicitDispatchCreatesTasksAndPreservesTaskText() {
        assertEquals("Составь план проверки.", LanaTeamVoice.task("Лана, передай команде задание: Составь план проверки."))
        assertEquals("Сколько будет 2+2?", LanaTeamVoice.task("Отправь помощнику OpenAI: Сколько будет 2+2?"))
        for (q in listOf("Можешь передать задание команде?", "Что такое команда", "Расскажи про ChatGPT", "Передай команде")) assertNull(q, LanaTeamVoice.task(q))
    }
    @Test fun explicitChatGptAliasesDispatchOnlyTheTaskAndDoNotClaimChatAccess() {
        for (receiver in listOf("ChatGPT", "Chat GPT", "чатгпт", "чату GPT", "чату джипити")) {
            assertEquals("Проверь план.", LanaTeamVoice.task("Лана, передай $receiver: Проверь план."))
            assertNull(LanaTeamVoice.task("Передай $receiver"))
            assertNull(LanaTeamVoice.task("Расскажи про $receiver"))
        }
        assertNull(LanaTeamVoice.task("Передай ChatGPTврагам: секрет"))
        assertTrue(LanaTeamVoice.localReply("Ты подключена к ChatGPT?")!!.contains("не управляю"))
    }
    @Test fun disconnectedProviderCommandsNeverCreateOpenAITask() {
        for (words in listOf(
            "Лана, передай Claude задание: проверь проект",
            "Отправь помощнику Manus: напиши код",
            "Передай Клауду новую задачу",
            "Попроси Клода проверить проект",
            "Дай задание Манусу составить план"
        )) {
            assertTrue(words, LanaTeamVoice.unavailableProvider(words))
            assertNull(words, LanaTeamVoice.task(words))
            assertTrue(words, LanaTeamVoice.localReply(words)!!.contains("Ничего не отправлено"))
        }
        for (words in listOf("Расскажи про Claude", "Что такое Manus", "Передай OpenAI: тест",
                "Передай команду", "Передай ClaudeBot пароль")) {
            assertFalse(words, LanaTeamVoice.unavailableProvider(words))
        }
        assertEquals("тест", LanaTeamVoice.task("Передай OpenAI: тест"))
        assertNull(LanaTeamVoice.localReply("Расскажи про Claude"))
    }
    @Test fun screenOffVoiceStatusMatchesSamsungEvidenceWithoutPromising247() {
        val answer = LanaTeamVoice.localReply("Лана, ты работаешь круглосуточно?")!!
        assertTrue(answer.contains("Samsung"))
        assertTrue(answer.contains("24/7 не подтверждена"))
        assertTrue(answer.contains("11"))
        assertFalse(answer.contains("проверен разговор в открытом приложении"))
    }
    @Test fun CapabilityRepliesDoNotClaimChatOrOtherAgentAccess() {
        assertTrue(LanaTeamVoice.localReply("Кто в твоей команде?")!!.contains("не подключены"))
        assertTrue(LanaTeamVoice.localReply("Ты подключена к ChatGPT?")!!.contains("не управляю"))
        assertTrue(LanaTeamVoice.localReply("Ты работаешь круглосуточно?")!!.contains("не подтверждены"))
        assertNull(LanaTeamVoice.localReply("Как написать приложение"))
        assertTrue(LanaTeamVoice.wantsLastReply("Прочитай последний ответ команды!"))
        assertTrue(LanaTeamVoice.wantsLastReply("Ладно Прочитай последний ответ команды"))
        assertTrue(LanaTeamVoice.wantsLastReply("Последний ответ команды"))
        assertFalse(LanaTeamVoice.wantsLastReply("Прочитай последний ответ погодной команды"))
    }
}
