package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaDialogueRecallTest {
    @Test fun actualSamsungQuestionRoutesLocallyButOtherQuestionsAreNotIntercepted() {
        for(q in listOf("а что ты помнишь последние вчерашние разговоры наши",
            "Лана, что ты помнишь из наших разговоров?",
            "Вспомни нашу вчерашнюю беседу",
            "А о чём мы говорили в прошлой переписке?")) {
            assertTrue(q,LanaDialogueRecall.matches(q))
        }
        for(q in listOf("Что ты запомнила в заметках?", "О чём фильм Вчера?",
            "Как улучшить наш разговор", "Расскажи про память телефонов")) {
            assertFalse(q,LanaDialogueRecall.matches(q))
        }
    }
    @Test fun speaksOnlyRealStoredLocalUserTurnsWithoutPretendingTheyWereYesterday() {
        val turns = listOf(
            LanaLegacyTurn("user","Привет, Лана"),
            LanaLegacyTurn("assistant","Привет"),
            LanaLegacyTurn("user","Доброе утро"),
            LanaLegacyTurn("assistant","Доброе!"),
            LanaLegacyTurn("user","Отличное настроение"),
            LanaLegacyTurn("assistant","Рада помочь"),
        )
        val answer=LanaDialogueRecall.reply(true,turns)
        assertTrue(answer.contains("Привет, Лана"))
        assertTrue(answer.contains("Доброе утро"))
        assertTrue(answer.contains("Отличное настроение"))
        assertFalse(answer.contains("Рада помочь"))
        assertTrue(answer.contains("не могу подтвердить"))
        assertTrue(answer.contains("История ChatGPT не подключена"))
    }
    @Test fun emptyAndUnreadableHistoryDoNotCausePhantomPast() {
        val empty=LanaDialogueRecall.reply(true,emptyList())
        assertTrue(empty.contains("нет сохранённых"))
        assertFalse(empty.contains("никогда не сохраня"))
        val unreadable=LanaDialogueRecall.reply(true,listOf(LanaLegacyTurn("user","secret")),true)
        assertTrue(unreadable.contains("не читается"))
        assertFalse(unreadable.contains("secret"))
    }
    @Test fun directModeDoesNotPretendLegacyHistoryIsAccessible() {
        val answer=LanaDialogueRecall.reply(false,listOf(LanaLegacyTurn("user","private")))
        assertTrue(answer.contains("нет подтверждённого доступа"))
        assertFalse(answer.contains("private"))
    }
}
