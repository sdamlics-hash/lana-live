package ai.dreibus.lanalive

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LanaJournalReportTest {
    @Test fun archiveIsReversibleAndCannotHideRunningRequests() {
        var saved = "[]"
        val journal = LanaTeamJournal(saved) { saved = it; true }
        val id = journal.begin("Задание")
        try { journal.archive(id, true); fail("Running must stay visible") } catch (_: IllegalStateException) { }
        journal.finish(id, "Ответ", null)
        journal.archive(id, true)
        val reopened = LanaTeamJournal(saved) { saved = it; true }
        assertEquals(0, reopened.filtered("", "").length())
        assertEquals("Ответ", reopened.filtered("", "", archived = true).getJSONObject(0).getString("answer"))
        reopened.archive(id, false)
        assertEquals(1, reopened.filtered("", "").length())
    }
    @Test fun orderAndSummaryAndExportPreserveAllMatchingEntries() {
        val journal = LanaTeamJournal("[]") { true }
        val a = journal.begin("Первое"); journal.finish(a, "Ответ 1", null)
        val b = journal.begin("Второе"); journal.finish(b, null, "Ошибка связи")
        assertEquals(a, journal.filtered("", "", oldestFirst = true).getJSONObject(0).getString("id"))
        assertEquals(b, journal.filtered("", "").getJSONObject(0).getString("id"))
        val text = LanaJournalReport.export(journal.records())
        assertTrue(text.contains("Ответ 1") && text.contains("Ошибка связи"))
        assertTrue(LanaJournalReport.summary(journal.records()).contains("ошибки: 1"))
        assertEquals("", LanaJournalReport.export(JSONArray()))
    }
    @Test fun unknownAndChangedClockDurationsAreNotFabricated() {
        assertEquals("Длительность не измерена", LanaJournalReport.duration(JSONObject()))
        assertTrue(LanaJournalReport.duration(JSONObject().put("createdAt", 20).put("finishedAt", 10)).contains("неизвестна"))
    }
    @Test fun existingHundredEntryJournalStillAcceptsNewWork() {
        val rows = JSONArray()
        repeat(100) { i -> rows.put(JSONObject().put("id", "$i").put("task", "Задание $i").put("status", "review").put("answer", "Ответ")) }
        val journal = LanaTeamJournal(rows.toString()) { true }
        journal.begin("Следующее")
        assertEquals(101, journal.records().length())
    }
}
