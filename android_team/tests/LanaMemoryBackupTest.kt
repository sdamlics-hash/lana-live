package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaMemoryBackupTest {
    private val records = """{"tasks":[{"id":"a","title":"Проверить Лану","done":false}],"notes":["Зелёный образ 🙂"]}"""
    @Test fun roundTripPreservesDataAndHasNoConnectionSettings() {
        val encoded = LanaMemoryBackup.encode(records)
        val restored = LanaMemoryBackup.decode(encoded.toByteArray())
        assertEquals(1, restored.tasks); assertEquals(1, restored.notes)
        assertEquals(LanaMemoryBackup.validate(records).records, restored.records)
        assertFalse(encoded.contains("token"))
    }
    @Test fun invalidOrForeignDataIsRejectedBeforeAnyWrite() {
        for (raw in listOf("{}", "broken", LanaMemoryBackup.encode(records).replace("LANA_MEMORY", "OTHER"), LanaMemoryBackup.encode(records).replace("\"version\":1", "\"version\":2"))) {
            try { LanaMemoryBackup.decode(raw.toByteArray()); fail("Invalid backup accepted") } catch (_: Exception) {}
        }
        for (raw in listOf(records.replace("false", "\"false\""), records.replace("\"notes\":", "\"token\":\"private\",\"notes\":"))) {
            try { LanaMemoryBackup.validate(raw); fail("Invalid memory accepted") } catch (_: Exception) {}
        }
    }
    @Test fun oversizedInputIsRejected() {
        try { LanaMemoryBackup.decode(ByteArray(LanaMemoryBackup.MAX_BYTES+1)); fail("Oversize accepted") } catch (_: IllegalArgumentException) {}
    }
}
