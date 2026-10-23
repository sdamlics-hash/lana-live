package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaDurableHistoryTest {
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
