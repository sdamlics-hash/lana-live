package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

class LanaChatTimeoutTest {
    private class Stalled(private val stall: String) : HttpsURLConnection(URL("https://example.invalid")) {
        var disconnected = false
        var uploads = 0
        override fun connect() { if (stall == "connect") throw SocketTimeoutException("SECRET") }
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getOutputStream(): java.io.OutputStream {
            uploads++
            if (stall == "upload") throw SocketTimeoutException("SECRET")
            return ByteArrayOutputStream()
        }
        override fun getResponseCode(): Int {
            if (stall == "headers") throw SocketTimeoutException("SECRET")
            return 200
        }
        override fun getInputStream(): InputStream = object : InputStream() {
            override fun read(): Int = throw SocketTimeoutException("SECRET")
        }
    }
    @Test fun identifiesEachTimeoutWithoutRetryOrSecretAndDisconnects() {
        for (stage in listOf("connect", "upload", "headers", "body")) {
            val connection = Stalled(stage)
            var opens = 0
            try {
                LanaChatClient.ask("https://example.invalid", "x".repeat(32), "test", "Привет",
                    legacyMode = true, connectionFactory = { opens++; connection })
                fail("Expected timeout at $stage")
            } catch (error: LanaChatTimeout) {
                assertEquals(stage, error.stage)
                assertFalse(error.message.orEmpty().contains("SECRET"))
                assertTrue(error.cause is SocketTimeoutException)
            }
            assertEquals(1, opens)
            assertTrue(connection.disconnected)
            assertEquals(if (stage == "connect") 0 else 1, connection.uploads)
            assertEquals(8000, connection.connectTimeout)
            assertEquals(32000, connection.readTimeout)
        }
    }
}
