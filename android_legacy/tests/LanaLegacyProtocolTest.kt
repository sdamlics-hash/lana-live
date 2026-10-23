package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaLegacyProtocolTest {
    @Test fun exactObservedLegacySchemaAndReplyField() {
        val body = LanaChatProtocol.request("id", "Вопрос", emptyList(), true, listOf(LanaLegacyTurn("user","Раньше"),LanaLegacyTurn("assistant","Ответ")))
        assertEquals(setOf("message","history"),body.keys().asSequence().toSet())
        assertEquals("Вопрос",body.getString("message"))
        assertEquals("user",body.getJSONArray("history").getJSONObject(0).getString("role"))
        assertEquals("Да",LanaChatProtocol.answer("{\"reply\":\"Да\"}",true))
    }
    @Test fun modernSchemaRemainsSeparate() {
        val body = LanaChatProtocol.request("id", "Вопрос", listOf("Память"), false, listOf(LanaLegacyTurn("user","Не отправлять")))
        assertEquals(setOf("conversation_id","text","context_notes","memory_consent"),body.keys().asSequence().toSet())
        assertTrue(body.getBoolean("memory_consent"));assertFalse(body.toString().contains("Не отправлять"))
        assertEquals("Да",LanaChatProtocol.answer("{\"answer\":\"Да\"}",false))
    }
    @Test fun notesOnlyEnterLegacyMessageWhenExplicitlySelected() {
        val empty = LanaChatProtocol.request("id","Вопрос",emptyList(),true,emptyList())
        assertEquals("Вопрос",empty.getString("message"))
        val chosen=LanaChatProtocol.request("id","Вопрос",listOf("Выбранная заметка"),true,emptyList())
        assertTrue(chosen.getString("message").contains("Выбранная заметка"))
        assertFalse(chosen.has("memory_consent"));assertEquals(0,chosen.getJSONArray("history").length())
    }
    @Test fun wrongResponseFieldsAndNonTextCannotMasqueradeAsAnswers() {
        for (text in listOf("{}","{\"answer\":\"wrong\"}","{\"reply\":true}","{\"reply\":123}","{\"reply\":\" \"}")) {
            try { LanaChatProtocol.answer(text,true);fail("Should reject invalid legacy response") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun unprivilegedHistoryAndBoundsAreEnforced() {
        for (history in listOf(listOf(LanaLegacyTurn("system","No")),List(17){LanaLegacyTurn("user","x")},List(4){LanaLegacyTurn("user","x".repeat(5000))})) {
            try { LanaChatProtocol.request("id","Вопрос",emptyList(),true,history);fail("Must reject invalid history") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun historyScopeAndAcceptedTurnsOnly() {
        val h=LanaLegacyHistory()
        assertTrue(h.snapshot("A").isEmpty());h.commit("A","Вопрос","Ответ")
        assertEquals(2,h.snapshot("A").size)
        assertTrue(h.snapshot("B").isEmpty());h.commit("A","Поздний","Не сохранять")
        assertTrue(h.snapshot("B").isEmpty())
        repeat(12){h.commit("B","x".repeat(2400),"y".repeat(5000))}
        val history=h.snapshot("B")
        assertTrue(history.size<=16 && history.sumOf{it.content.length}<=16000)
        assertEquals("user",history.first().role);assertEquals("assistant",history.last().role)
    }
    @Test fun quotaCountsFailedAttemptsAndSurvivesScopeChangesByUsingOneLedger() {
        val now=100000000L
        assertTrue(LanaLegacyQuotaPolicy.allowed(emptyList(),now))
        assertFalse(LanaLegacyQuotaPolicy.allowed(List(30){now-1000},now))
        assertFalse(LanaLegacyQuotaPolicy.allowed(List(100){now-7200000},now))
        assertTrue(LanaLegacyQuotaPolicy.allowed(List(100){now-86400001},now))
        assertFalse(LanaLegacyQuotaPolicy.allowed(List(30){now+1000},now))
    }
}
