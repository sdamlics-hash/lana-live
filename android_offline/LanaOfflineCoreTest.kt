package ai.dreibus.lanalive
import org.junit.Test
import org.junit.Assert.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class LanaOfflineCoreTest {
    private val fixed=Clock.fixed(Instant.parse("2026-11-25T08:38:00Z"),ZoneId.of("Europe/Berlin"))
    private val core=LanaOfflineCore(fixed)
    @Test fun time() { assertTrue(core.answer("Лана, сколько времени?").contains("09:38")) }
    @Test fun timeVariant() { assertTrue(core.answer("LANA: сколько время сейчас!").contains("09:38")) }
    @Test fun date() { assertTrue(core.answer("Какое сегодня число?").contains("25 ноября 2026")) }
    @Test fun greeting() { assertEquals("Я на связи.",core.answer("Доброе утро, Лана!")) }
    @Test fun conversationStatus() { assertTrue(core.answer("Как твои дела сегодня").contains("локальном")) }
    @Test fun unknownIsHonest() { assertEquals(LanaOfflineCore.NEED_MODEL,core.answer("Объясни квантовую физику")) }
    @Test fun blankAndLong() { assertTrue(core.answer(" ").contains("вопрос")); assertTrue(core.answer("я".repeat(4001)).contains("длинный")) }
    @Test fun offlineStatus() { assertTrue(core.answer("Режим работы").contains("не отправляются")) }
    @Test fun teamDisabled() { assertEquals(LanaOfflineCore.TEAM_DISABLED,LanaOfflineCore.capability("команда","Есть отправка OpenAI")); assertNull(LanaOfflineCore.capability("x",null)) }
    @Test fun repeatedTurns() { repeat(200) { assertTrue(core.answer("Который час").contains("09:38")); assertEquals(LanaOfflineCore.NEED_MODEL,core.answer("Свободный вопрос $it")) } }
    @Test fun oneHundredDialogueRegressions() {
        var checked = 0
        val prefixes = listOf("", "Лана, ", "LANA: ", "ладно ", "Лана, пожалуйста, ")
        val marks = listOf("", "?", "!", "…")
        for (prefix in prefixes) for (mark in marks) {
            assertTrue(core.answer(prefix + "приятно, сколько время" + mark).contains("09:38"))
            checked++
            assertEquals("Я на связи.", core.answer(prefix + "ты меня слышишь" + mark))
            checked++
            assertTrue(core.answer(prefix + "повторяешь всё за мной да" + mark).startsWith("Поняла"))
            checked++
            val question = prefix + "Объясни тему" + mark
            assertEquals(LanaOfflineCore.REPHRASE, LanaOfflineCore.checkedModelReply(question, "Текущий вопрос: " + question))
            assertEquals(LanaOfflineCore.REPHRASE, LanaOfflineCore.checkedModelReply(question, question.uppercase(java.util.Locale.ROOT)))
            checked++
            assertEquals("Ответ по смыслу.", LanaOfflineCore.checkedModelReply(question, "Ответ по смыслу."))
            assertEquals(LanaOfflineCore.NEED_MODEL, core.answer(question))
            checked++
        }
        assertEquals(100, checked)
    }
    @Test fun handlesTabletQuestionAndFeedbackFromSamsung() {
        val q = "Что ты на планшете ещё можешь посмотреть?"
        assertEquals(LanaOfflineCore.REPHRASE,
            LanaOfflineCore.checkedModelReply(q, "Ты на планшете ещё можешь посмотреть."))
        assertTrue(core.answer(q).contains("Экран других приложений сейчас мне недоступен"))
        assertTrue(core.answer("Ты повторяешь мои вопросы").startsWith("Поняла"))
        assertTrue(core.answer("Ты повторяешь за мной").startsWith("Поняла"))
    }
    @Test fun keepsShortMeaningfulAnswersAndRejectsBarePromptHeader() {
        assertEquals("Дома.", LanaOfflineCore.checkedModelReply("Где дома?", "Дома."))
        assertEquals("В настройках Ланы.",
            LanaOfflineCore.checkedModelReply("Где мне найти это на планшете?", "В настройках Ланы."))
        assertEquals(LanaOfflineCore.REPHRASE,
            LanaOfflineCore.checkedModelReply("Что ты умеешь?", "Текущий вопрос:"))
    }
    @Test fun oneHundredHistoryBoundaryCases() {
        val lengths = listOf(1, 2, 3, 30, 99, 199, 200, 201, 400, 2400)
        for (pairs in 0..9) for (length in lengths) {
            val turns = (0 until pairs).flatMap { index ->
                listOf(LanaLegacyTurn("user", "Я".repeat(length)),
                    LanaLegacyTurn("assistant", "Ответ №" + index))
            }
            val bounded = LanaOfflineCore.boundedHistory(turns)
            assertEquals(minOf(pairs, 2) * 2, bounded.size)
            assertEquals(turns.takeLast(4).map { it.role }, bounded.map { it.role })
            assertEquals(turns.takeLast(4).map { it.content.take(200) }, bounded.map { it.content })
        }
    }

    @Test fun invalidTurnPairsCannotBecomeInstructions() {
        val bad = listOf(LanaLegacyTurn("system", "Служебная строка"),
            LanaLegacyTurn("assistant", "Ответ"))
        assertTrue(LanaOfflineCore.boundedHistory(bad).isEmpty())
        assertTrue(LanaOfflineCore.boundedHistory(listOf(LanaLegacyTurn("user", " "))).isEmpty())
    }
    @Test fun sanitizesOnlyCompleteLeadingReasoning() {
        assertEquals("Рига.", LanaOfflineCore.visibleModelReply("<think>Скрыто</think>\nРига."))
        assertEquals("17", LanaOfflineCore.visibleModelReply("<THINK></THINK> <think>другое</think>17"))
        assertNull(LanaOfflineCore.visibleModelReply("<think>Скрыто"))
        assertNull(LanaOfflineCore.visibleModelReply("<think>внешний<think>внутренний</think>17"))
        assertNull(LanaOfflineCore.visibleModelReply("Рига.<think>Скрыто</think>"))
        assertNull(LanaOfflineCore.visibleModelReply("</think>Рига."))
        assertNull(LanaOfflineCore.visibleModelReply("<think>Скрыто</think> "))
    }

    @Test fun truncationPreservesSurrogatePairsInModelResponseAndHistory() {
        val reply = "a".repeat(4999) + "🙂" + "x"
        assertEquals("a".repeat(4999), LanaOfflineCore.visibleModelReply(reply))
        val history = listOf(LanaLegacyTurn("user", "a".repeat(199) + "🙂"),
            LanaLegacyTurn("assistant", "Готово."))
        assertEquals("a".repeat(199), LanaOfflineCore.boundedHistory(history).first().content)
    }
}
