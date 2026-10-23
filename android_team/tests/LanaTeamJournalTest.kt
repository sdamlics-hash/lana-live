package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaTeamJournalTest {
    @Test fun responseRequiresReviewAndSurvivesReload() {
        var stored = "[]"
        val journal = LanaTeamJournal(stored) { stored = it; true }
        val id = journal.begin("Составь план проверки Ланы")
        journal.finish(id, "План проверки", null)
        val reopened = LanaTeamJournal(stored) { stored = it; true }
        assertEquals("review", reopened.records().getJSONObject(0).getString("status"))
        reopened.confirm(id)
        assertEquals("verified", LanaTeamJournal(stored) { true }.records().getJSONObject(0).getString("status"))
    }
    @Test fun duplicateAndConcurrentRequestsCannotBeDispatched() {
        val journal = LanaTeamJournal("[]") { true }
        journal.begin("Первое задание")
        for (task in listOf("Первое задание", "Второе задание")) {
            try { journal.begin(task); fail("Must reject duplicate or concurrent dispatch") }
            catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { }
        }
        assertEquals(1, journal.records().length())
    }
    @Test fun interruptedRequestIsNotResentOrMarkedSuccessful() {
        var stored = "[]"
        val journal = LanaTeamJournal(stored) { stored = it; true }
        journal.begin("Проверь план")
        val restored = LanaTeamJournal(stored) { stored = it; true }
        assertEquals("interrupted", restored.records().getJSONObject(0).getString("status"))
    }
    @Test fun persistenceFailurePreventsDispatchAndRetainsOldState() {
        val journal = LanaTeamJournal("[]") { false }
        try { journal.begin("Задание"); fail("Must fail before dispatch") }
        catch (_: IllegalStateException) { }
        assertEquals(0, journal.records().length())
    }
    @Test fun errorIsDurableAndCannotBeConfirmedAsSuccess() {
        var stored = "[]"
        val journal = LanaTeamJournal(stored) { stored = it; true }
        val id = journal.begin("Задание")
        journal.finish(id, null, "Нет связи")
        try { journal.confirm(id); fail("Cannot verify failure") } catch (_: IllegalStateException) { }
        assertEquals("error", LanaTeamJournal(stored) { true }.records().getJSONObject(0).getString("status"))
    }
}
