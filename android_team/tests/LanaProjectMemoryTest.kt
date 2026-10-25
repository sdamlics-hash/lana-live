package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class LanaProjectMemoryTest {
    @Test fun explicitlySavedFactsAreProjectScopedAndTimestamped() {
        var raw=LanaProjectMemory.EMPTY
        fun ask(words:String):String? = LanaProjectMemory(raw,{raw=it;true},{1792915200000L}).handle(words)
        assertTrue(ask("Лана, запомни для проекта GOLD: Вход M1 подтверждает M5")!!.contains("Сохранила"))
        assertTrue(ask("запомни для проекта Лана: Голос должен работать с выключенным экраном")!!.contains("Сохранила"))
        val gold=ask("Что ты помнишь по проекту GOLD")!!
        assertTrue(gold.contains("M1")); assertTrue(gold.contains("UTC"))
        assertFalse(gold.contains("Голос должен"))
        assertTrue(ask("Покажи память проекта Лана")!!.contains("Голос должен"))
        assertFalse(ask("Найди в памяти проекта GOLD: голос")!!.contains("Голос должен"))
        assertTrue(ask("Покажи проекты памяти")!!.contains("GOLD"))
        val parsed=JSONObject(raw).getJSONArray("facts")
        assertEquals(2,parsed.length())
        assertEquals("user_explicit",parsed.getJSONObject(0).getString("source"))
        assertEquals(1792915200000L,parsed.getJSONObject(0).getLong("createdAt"))
        assertTrue(LanaProjectMemory(raw,{true}).handle("Запомни для проекта gold: Вход M1 подтверждает M5")!!.contains("Дубликат"))
    }
    @Test fun unrelatedSpeechNeverBecomesSavedMemoryOrModelShortcut() {
        for(q in listOf("Что такое проект Лана?", "Запомни что я люблю листья",
            "Память компьютера", "Как дела с GOLD", "Покажи мои задачи")) {
            assertFalse(q,LanaProjectMemory.matches(q))
        }
        assertTrue(LanaProjectMemory.matches("Лана, покажи память проекта GOLD"))
    }
    @Test fun noPhantomFactAfterFailedWriteAndRetry() {
        var data=LanaProjectMemory.EMPTY
        var writable=false
        val memory=LanaProjectMemory(data,{ if(writable){data=it;true}else false },{1792915200000L})
        assertTrue(memory.handle("Запомни для проекта Лана: ошибка записи")!!.contains("Не удалось"))
        assertFalse(memory.handle("Покажи память проекта Лана")!!.contains("ошибка записи"))
        writable=true
        assertTrue(memory.handle("Запомни для проекта Лана: подтверждённый факт")!!.contains("Сохранила"))
        assertFalse(data.contains("ошибка записи"))
    }
    @Test fun memoryBackupSchemaRejectsForeignSecretsAndMalformedRecords() {
        var data=LanaProjectMemory.EMPTY
        LanaProjectMemory(data,{data=it;true}).handle("Запомни для проекта Лана: версия 1043")
        assertEquals(LanaProjectMemory.validate(data), LanaProjectMemory.validate(data))
        val arr=JSONObject(data)
        arr.getJSONArray("facts").getJSONObject(0).put("token","sk-secret")
        try { LanaProjectMemory.validate(arr.toString()); fail("extra field accepted") } catch (_:IllegalArgumentException) {}
        try { LanaProjectMemory.validate("""{"version":3,"facts":[]}""");fail("future version accepted") } catch (_:IllegalArgumentException) {}
        try { LanaProjectMemory.validate("X".repeat(LanaProjectMemory.MAX_BYTES+1));fail("oversized accepted") } catch (_:IllegalArgumentException) {}
    }
}
