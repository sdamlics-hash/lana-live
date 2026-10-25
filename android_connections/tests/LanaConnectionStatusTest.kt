package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaConnectionStatusTest {
    @Test fun statusesNeverInferConnectionFromKeyOrMock() {
        assertFalse(LanaConnectionStatus.render("unverified","not_connected","readable").contains("API — подключён"))
        assertFalse(LanaConnectionStatus.render("test_mode","not_connected","readable").contains("API — подключён"))
        assertTrue(LanaConnectionStatus.render("verified_recently","not_connected","readable").contains("успешный ответ"))
        assertTrue(LanaConnectionStatus.render("unknown","connected","unknown").contains("статус не подтверждён"))
    }
    @Test fun teamConnectionsAreHonestAndBackwardCompatible() {
        val legacy = LanaConnectionStatus.render("verified_recently","not_connected","readable")
        assertTrue(legacy.contains("Claude — подключение не подтверждено"))
        assertTrue(legacy.contains("Manus — подключение не подтверждено"))
        val modern = LanaConnectionStatus.render("verified_recently","not_connected","readable","not_configured","not_configured")
        assertTrue(modern.contains("Claude — не настроен"))
        assertTrue(modern.contains("Manus — не настроен"))
        assertTrue(modern.contains("OpenAI API — подключён на момент проверки"))
        val failure = LanaConnectionStatus.render("unavailable","not_connected","unavailable","unavailable","unknown")
        assertFalse(failure.contains("OpenAI API — подключён"))
        assertFalse(failure.contains("Manus — подключение подтверждено"))
    }
    @Test fun explicitRoutesAvoidDuplicateSuffixAndKeepProxyPrefix() {
        assertEquals("https://example.test/v1/chat",LanaConnectionStatus.chatUrl("https://example.test"))
        assertEquals("https://example.test/v1/chat",LanaConnectionStatus.chatUrl("https://example.test/v1/chat/"))
        assertEquals("https://example.test/lana/v1/lana/chat",LanaConnectionStatus.chatUrl("https://example.test/lana/v1/lana/chat"))
        assertEquals("https://example.test/lana",LanaConnectionStatus.root("https://example.test/lana/v1/lana/chat"))
    }
    @Test fun rejectsInsecureOrCredentialUrls() {
        for (s in listOf("http://example.test","https://user:secret@example.test","https://example.test?token=secret","https://example.test#key")) {
            try { LanaConnectionStatus.root(s); fail("URL should be rejected") } catch (_: IllegalArgumentException) {}
        }
    }
}
