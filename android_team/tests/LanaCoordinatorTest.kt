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
    @Test fun memoryUsesActualResultWithItsReviewStateAndNeverTruncates() {
        val rows = JSONArray().put(row())
        val command = LanaCoordinator.memoryCommand("Сохрани последний ответ команды в память", rows)!!
        var saved = "{\"tasks\":[],\"notes\":[]}"
        val memory = LanaLocalWorkspace(saved) { saved = it; true }
        assertTrue(memory.handle(command)!!.contains("Запомнила"))
        val note = JSONObject(saved).getJSONArray("notes").getString(0)
        assertTrue(note.contains("ожидает проверки") && note.contains("Нужно проверить звук"))
        assertNull(LanaCoordinator.memoryCommand("Просто расскажи о памяти", rows))
        try {
            LanaCoordinator.memoryCommand("Сохрани последний ответ команды в память", JSONArray().put(row(answer="я".repeat(3000))))
            fail("Must reject oversized memory, not truncate")
        } catch (_: IllegalArgumentException) {}
    }
    @Test fun incompleteAndUnrelatedCommandsDoNotDispatch() {
        assertFalse(LanaCoordinator.handles("Уточни у командывания план"))
        assertFalse(LanaCoordinator.handles("Можешь уточнить у команды?"))
        try { LanaCoordinator.followUp("Уточни у команды", JSONArray().put(row())); fail() } catch (_: IllegalArgumentException) {}
        assertNull(LanaCoordinator.followUp("Какой статус погоды?", JSONArray().put(row())))
    }
}
