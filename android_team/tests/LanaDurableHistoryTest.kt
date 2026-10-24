package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaDurableHistoryTest {
    @Test fun unreadableSavedContextIsReportedBeforeAnyAnswerAndNeverLeaksStorageDetails() {
        val messages = mutableListOf<String>()
        var writes = 0
        val ledger = LanaLegacyHistory({ throw IllegalStateException("private transcript and credentials") }, { _, _ -> writes++; true })
        assertTrue(ledger.snapshot("A",messages::add).isEmpty())
        assertEquals(1,messages.size)
        assertEquals(ledger.storageWarning,messages.single())
        assertFalse(messages.single().contains("private"))
        assertFalse(ledger.commit("A","new question","new answer"))
        assertEquals(0,writes)
        assertEquals(listOf("new question","new answer"),ledger.snapshot("A",messages::add).map { it.content })
        assertEquals(2,messages.size)
    }
    @Test fun diagnosticFailureCannotDiscardLiveContextAndNewScopeClearsWarning() {
        var writes = 0
        val ledger = LanaLegacyHistory({ if (it == "A") "corrupt" else null }, { _, _ -> writes++; true })
        assertTrue(ledger.snapshot("A") { throw IllegalStateException("observer failed") }.isEmpty())
        assertFalse(ledger.commit("A","question","answer"))
        assertEquals(2,ledger.snapshot("A") { throw IllegalStateException("observer failed") }.size)
        var warnings = 0
        assertTrue(ledger.snapshot("B") { warnings++ }.isEmpty())
        assertEquals(0,warnings)
        assertNull(ledger.storageWarning)
        assertTrue(ledger.commit("B","fresh","answer"))
        assertEquals(1,writes)
    }
    @Test fun failedSaveWarningIsReportedOnNextQuestionAndClearedAfterRecovery() {
        var writable = false
        val ledger = LanaLegacyHistory({ null }, { _, _ -> writable })
        ledger.snapshot("A")
        assertFalse(ledger.commit("A","question","answer"))
        val warnings = mutableListOf<String>()
        assertEquals(2,ledger.snapshot("A",warnings::add).size)
        assertEquals(1,warnings.size)
        writable = true
        assertTrue(ledger.commit("A","follow up","reply"))
        assertEquals(4,ledger.snapshot("A",warnings::add).size)
        assertEquals(1,warnings.size)
    }
    @Test fun recentTopicReachesWireAfterRestartAndOlderTurnsAreEvictedInPairs() {
        var disk: String? = null
        val first = LanaLegacyHistory({ disk }, { _, raw -> disk=raw; true })
        first.snapshot("same-device-session")
        repeat(6) { first.commit("same-device-session", "old question $it", "old answer $it") }
        first.commit("same-device-session", "Предложи занятие на вечер", "Можно почитать книгу.")
        first.commit("same-device-session", "А если я устал?", "Тогда отдохни и ложись спать.")
        val restarted = LanaLegacyHistory({ if (it == "same-device-session") disk else null })
        val question = "А что ты предложила мне перед этим?"
        val body = LanaChatProtocol.request("id",LanaDialoguePrompt.build(question),emptyList(),true,
            restarted.snapshot("same-device-session"))
        val wire = body.getJSONArray("history")
        assertEquals(12,wire.length())
        assertEquals("old question 2",wire.getJSONObject(0).getString("content"))
        assertEquals("user",wire.getJSONObject(10).getString("role"))
        assertEquals("А если я устал?",wire.getJSONObject(10).getString("content"))
        assertEquals("assistant",wire.getJSONObject(11).getString("role"))
        assertEquals("Тогда отдохни и ложись спать.",wire.getJSONObject(11).getString("content"))
        assertTrue(body.getString("message").endsWith(question))
        assertTrue(restarted.snapshot("different-session").isEmpty())
    }

    @Test fun acceptedDialogueSurvivesRecreationWithinSameScope() {
        val disk = mutableMapOf<String,String>()
        fun ledger() = LanaLegacyHistory({ disk[it] }, { scope, raw -> disk[scope] = raw; true })
        val first = ledger(); first.snapshot("A"); assertTrue(first.commit("A", "17 плюс 26", "43"))
        val second = ledger(); assertEquals(listOf("17 плюс 26", "43"),second.snapshot("A").map { it.content })
        assertTrue(second.snapshot("B").isEmpty()); assertFalse(second.commit("A", "late", "discard"))
        assertTrue(second.snapshot("B").isEmpty())
    }
    @Test fun corruptOrPrivilegedHistoryIsPreservedWithoutBeingSentOrOverwritten() {
        for (raw in listOf("bad", "[{\"role\":\"user\",\"content\":\"half\"}]", "[{\"role\":\"system\",\"content\":\"x\"},{\"role\":\"assistant\",\"content\":\"x\"}]")) {
            var writes = 0
            val ledger = LanaLegacyHistory({raw},{ _, _ -> writes++; true })
            assertTrue(ledger.snapshot("A").isEmpty()); assertNotNull(ledger.storageWarning)
            assertFalse(ledger.commit("A","new","reply")); assertEquals(0,writes)
        }
    }
    @Test fun writeFailureKeepsLiveContextAndReportsFailure() {
        val ledger = LanaLegacyHistory({null},{ _, _ -> false }); ledger.snapshot("A")
        assertFalse(ledger.commit("A","question","answer")); assertEquals(2,ledger.snapshot("A").size)
        assertNotNull(ledger.storageWarning)
    }
    @Test fun boundedHistoryAlwaysKeepsCompletePairs() {
        var raw = "[]"
        val ledger = LanaLegacyHistory({raw},{ _, value -> raw=value; true }); ledger.snapshot("A")
        repeat(30) { assertTrue(ledger.commit("A","x".repeat(2400),"y".repeat(5000))) }
        val restored = LanaLegacyHistory({raw}); val turns = restored.snapshot("A")
        assertEquals(4,turns.size); assertEquals("user",turns.first().role); assertEquals("assistant",turns.last().role)
    }
}
