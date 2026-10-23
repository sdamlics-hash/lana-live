package ai.dreibus.lanalive
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class LanaCoordinatorTest {
    private fun row(status:String="review", answer:String="Нужно проверить звук") = JSONObject().put("id","a").put("task","План Ланы").put("status",status).put("answer",answer)
    @Test fun summaryDistinguishesAcceptedTextFromExecutionAndIgnoresArchive() {
        val r=JSONArray().put(row("verified")).put(row("review").put("archived",true))
        assertTrue(LanaCoordinator.summary(r).contains("Приняты: 1"))
        assertTrue(LanaCoordinator.summary(r).contains("Ждут оценки: 0"))
        assertTrue(LanaCoordinator.reply("Лана, прочитай последний ответ команды",r)!!.contains("выполнение действий не проверено"))
    }
    @Test fun followUpKeepsOriginalTaskAndAnswerWithoutSilentTruncation() {
        val r=JSONArray().put(row())
        val task=LanaCoordinator.followUp("Уточни у команды: Как проверить?",r)!!
        assertTrue(task.contains("План Ланы") && task.contains("Нужно проверить звук") && task.contains("Как проверить?"))
        assertNull(LanaCoordinator.followUp("Как проверить звук?",r))
        try { LanaCoordinator.followUp("Уточни у команды: как?",JSONArray().put(row(answer="x".repeat(1200)))); fail() } catch (_:IllegalArgumentException) {}
        try { LanaCoordinator.followUp("Уточни у команды: как?",JSONArray()); fail() } catch (_:IllegalArgumentException) {}
    }
    @Test fun noFabricatedResultsWhenEmpty() {
        assertEquals("Ответов, ожидающих твоей оценки, нет.",LanaCoordinator.reply("Что ждёт проверки?",JSONArray()))
        assertEquals("Сохранённого ответа команды пока нет.",LanaCoordinator.reply("Прочитай последний ответ команды",JSONArray()))
        assertNull(LanaCoordinator.reply("Расскажи про команду корабля",JSONArray()))
    }
}
