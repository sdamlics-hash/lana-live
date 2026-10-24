package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

class LanaHttpCancellationTest {
    private class Connection(val onHeaders: () -> Unit = {}) : HttpsURLConnection(URL("https://example.invalid")) {
        var closes=0;var connects=0;var bodyReads=0
        override fun connect() { connects++ }
        override fun disconnect() { closes++ }
        override fun usingProxy()=false
        override fun getCipherSuite()="test"
        override fun getLocalCertificates():Array<Certificate>?=null
        override fun getServerCertificates():Array<Certificate> = emptyArray()
        override fun getOutputStream()=ByteArrayOutputStream()
        override fun getResponseCode():Int { onHeaders();return 200 }
        override fun getInputStream():java.io.InputStream { bodyReads++;return ByteArrayInputStream("{\"reply\":\"late\"}".toByteArray()) }
    }
    @Test fun cancellationBeforeDispatchMakesNoConnection() {
        val cancel=LanaHttpCancellation();cancel.cancel();var opens=0
        try {
            LanaChatClient.ask("https://example.invalid","x".repeat(32),"id","Привет",legacyMode=true,
                connectionFactory={ opens++;Connection() },cancellation=cancel)
            fail()
        } catch (_:InterruptedIOException) {}
        assertEquals(0,opens)
    }
    @Test fun cancellationDuringHeadersClosesConnectionAndNeverReturnsLateAnswer() {
        val cancel=LanaHttpCancellation();val conn=Connection { cancel.cancel() }
        try {
            LanaChatClient.ask("https://example.invalid","x".repeat(32),"id","Привет",legacyMode=true,
                connectionFactory={ conn },cancellation=cancel)
            fail()
        } catch (_:InterruptedIOException) {}
        assertEquals(1,conn.connects);assertTrue(conn.closes>=1);assertEquals(0,conn.bodyReads)
    }
    @Test fun doubleCancellationClosesOnlyOwnedConnectionAndReleaseCannotCloseNextRequest() {
        val cancel=LanaHttpCancellation();val conn=Connection();cancel.bind(conn)
        cancel.cancel();cancel.cancel();assertEquals(1,conn.closes)
        try { cancel.bind(Connection());fail() } catch (_:InterruptedIOException) {}
        val other=LanaHttpCancellation();val released=Connection();other.bind(released);other.release(released);other.cancel()
        assertEquals(0,released.closes)
    }
}
