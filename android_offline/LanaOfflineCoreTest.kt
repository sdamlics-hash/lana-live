package ai.dreibus.lanalive
import org.junit.Test
import org.junit.Assert.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class LanaOfflineCoreTest {
    private val fixed=Clock.fixed(Instant.parse("2026-11-25T08:38:00Z"),ZoneId.of("Europe/Berlin"))
    private val core=LanaOfflineCore(fixed)
    @Test fun time() { assertTrue(core.answer("Лана, сколько времени?").contains("09:38")) }
    @Test fun timeVariant() { assertTrue(core.answer("LANA: сколько время сейчас!").contains("09:38")) }
    @Test fun date() { assertTrue(core.answer("Какое сегодня число?").contains("25 ноября 2026")) }
    @Test fun greeting() { assertEquals("Я на связи.",core.answer("Доброе утро, Лана!")) }
    @Test fun conversationStatus() { assertTrue(core.answer("Как твои дела сегодня").contains("локальном")) }
    @Test fun unknownIsHonest() { assertEquals(LanaOfflineCore.NEED_MODEL,core.answer("Объясни квантовую физику")) }
    @Test fun blankAndLong() { assertTrue(core.answer(" ").contains("вопрос")); assertTrue(core.answer("я".repeat(4001)).contains("длинный")) }
    @Test fun offlineStatus() { assertTrue(core.answer("Режим работы").contains("не отправляются")) }
    @Test fun teamDisabled() { assertEquals(LanaOfflineCore.TEAM_DISABLED,LanaOfflineCore.capability("команда","Есть отправка OpenAI")); assertNull(LanaOfflineCore.capability("x",null)) }
    @Test fun repeatedTurns() { repeat(200) { assertTrue(core.answer("Который час").contains("09:38")); assertEquals(LanaOfflineCore.NEED_MODEL,core.answer("Свободный вопрос $it")) } }
}
