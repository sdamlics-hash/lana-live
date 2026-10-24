package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaBackgroundTeamTest {
    private class Transport : LanaConversationRuntime.Transport {
        var calls = 0; var cancellations = 0
        var callback: ((String?, String?) -> Unit)? = null
        override fun ask(request: LanaRuntimeRequest, complete: (String?, String?) -> Unit): LanaConversationRuntime.Cancel {
            calls++; callback = complete
            return LanaConversationRuntime.Cancel { cancellations++ }
        }
    }
    private fun request(task: String = "Проверь план") = LanaRuntimeRequest("https://example.invalid", "redacted", "task-id", task, "prompt", true, emptyList())
    @Test fun persistsBeforeDispatchAndCommitsOnlyOneResult() {
        var raw = "[]"
        val j = LanaTeamJournal(raw) { raw=it; true }; val t = Transport(); val d = LanaBackgroundTeam(j,t)
        var replies = 0
        d.submit("Проверь план",request()) { replies++ }
        assertEquals("running", org.json.JSONArray(raw).getJSONObject(0).getString("status"))
        assertTrue(d.running); assertEquals(1,t.calls)
        t.callback!!("Готов ответ",null); t.callback!!("Дубликат",null)
        assertEquals(1,replies); assertFalse(d.running)
        assertEquals("review",j.records().getJSONObject(0).getString("status"))
        assertEquals("Готов ответ",j.records().getJSONObject(0).getString("answer"))
    }
    @Test fun failedJournalWritePreventsNetworkCall() {
        val t=Transport(); val d=LanaBackgroundTeam(LanaTeamJournal("[]") { false },t)
        try { d.submit("Проверь план",request()) {}; fail() } catch (_:IllegalStateException) {}
        assertEquals(0,t.calls); assertFalse(d.running)
    }
    @Test fun stopPersistsInterruptedAndLateAnswerCannotSpeakOrResend() {
        val j=LanaTeamJournal("[]") { true };val t=Transport();val d=LanaBackgroundTeam(j,t);var replies=0
        d.submit("Проверь план",request()) { replies++ };d.stop();t.callback!!("Поздно",null)
        assertEquals(0,replies);assertEquals(1,t.calls);assertEquals(1,t.cancellations)
        assertEquals("interrupted",j.records().getJSONObject(0).getString("status"))
        assertFalse(d.running)
    }
    @Test fun errorDoesNotRetryAndParallelOrDuplicateTasksAreRejected() {
        val j=LanaTeamJournal("[]") { true };val t=Transport();val d=LanaBackgroundTeam(j,t)
        d.submit("Проверь план",request()) {}
        try { d.submit("Другое",request("Другое")) {};fail() } catch (_:IllegalStateException) {}
        t.callback!!(null,"Сбой связи")
        try { d.submit("Проверь план",request()) {};fail() } catch (_:IllegalArgumentException) {}
        assertEquals(1,t.calls);assertEquals("error",j.records().getJSONObject(0).getString("status"))
    }
    @Test fun synchronousTransportAndStorageFailureKeepReceivedAnswerVisible() {
        var writes=0
        val j=LanaTeamJournal("[]") { ++writes == 1 }
        val t=object : LanaConversationRuntime.Transport {
            override fun ask(request:LanaRuntimeRequest,complete:(String?,String?)->Unit):LanaConversationRuntime.Cancel {
                complete("Содержательный ответ",null)
                return LanaConversationRuntime.Cancel {}
            }
        }
        var reply="";val d=LanaBackgroundTeam(j,t)
        d.submit("Проверь план",request()) { reply=it }
        assertFalse(d.running);assertTrue(reply.contains("Не удалось сохранить"));assertTrue(reply.contains("Содержательный ответ"))
        assertEquals("running",j.records().getJSONObject(0).getString("status"))
    }
}
