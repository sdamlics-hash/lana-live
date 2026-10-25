package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class LanaProjectMemoryBackupTest {
    @Test fun formatRoundtripRetainsProjectAndNoCredentials() {
        var data=LanaProjectMemory.EMPTY
        LanaProjectMemory(data,{data=it;true}).handle("Запомни для проекта Лана: номер сборки 1043")
        val export=LanaProjectMemoryBackup.export(data,1792915200000L)
        assertEquals(LanaProjectMemory.validate(data),LanaProjectMemoryBackup.import(export))
        assertFalse(String(export).contains("api_key"))
        assertFalse(String(export).contains("bearer"))
    }
    @Test fun refusesUnknownFieldsAndIncompatibleVersions() {
        val good=JSONObject(String(LanaProjectMemoryBackup.export(LanaProjectMemory.EMPTY)))
        for (broken in listOf(
            JSONObject(good.toString()).put("version",3).toString(),
            JSONObject(good.toString()).put("token","private").toString(),
            JSONObject(good.toString()).put("format","FOREIGN").toString(),
            JSONObject(good.toString()).put("records",JSONObject("""{"version":2,"facts":[{"text":"no-source"}]}""")).toString()
        )) {
            try { LanaProjectMemoryBackup.import(broken.toByteArray());fail("bad backup accepted") }
            catch (_: Exception) {}
        }
    }
}
