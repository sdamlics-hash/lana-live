package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

class LanaSpeechCancellationTest {
    private class Connection(val onHeaders: () -> Unit = {}, val onRead: () -> Unit = {}) : HttpsURLConnection(URL("https://example.invalid")) {
        var opens=0; var closes=0; var reads=0
        override fun connect() { opens++ }
        override fun disconnect() { closes++ }
        override fun usingProxy()=false
        override fun getCipherSuite()="test"
        override fun getLocalCertificates():Array<Certificate>?=null
        override fun getServerCertificates():Array<Certificate> = emptyArray()
        override fun getOutputStream()=ByteArrayOutputStream()
        override fun getResponseCode():Int { onHeaders();return 200 }
        override fun getContentType()="audio/mpeg"
        override fun getInputStream():java.io.InputStream {
            reads++
            return object : ByteArrayInputStream(byteArrayOf(1,2,3)) {
                override fun read(b:ByteArray,off:Int,len:Int):Int { onRead(); return super.read(b,off,len) }
            }
        }
    }
    private fun request(c: LanaHttpCancellation, factory: (URL)->HttpsURLConnection) =
        LanaSpeechClient.audio("https://example.invalid","x".repeat(32),"Привет",c,factory)
    @Test fun stoppedBeforeDispatchNeverOpensConnection() {
        val c=LanaHttpCancellation();c.cancel();var opens=0
        try { request(c){opens++;Connection()};fail() } catch (_:InterruptedIOException) {}
        assertEquals(0,opens)
    }
    @Test fun stopDuringHeadersNeverReadsAudio() {
        val c=LanaHttpCancellation();val conn=Connection(onHeaders={c.cancel()})
        try { request(c){conn};fail() } catch (_:InterruptedIOException) {}
        assertEquals(0,conn.reads);assertTrue(conn.closes>=1)
    }
    @Test fun stopDuringBodyNeverReturnsLateAudio() {
        val c=LanaHttpCancellation();val conn=Connection(onRead={c.cancel()})
        try { request(c){conn};fail() } catch (_:InterruptedIOException) {}
        assertEquals(1,conn.reads);assertTrue(conn.closes>=1)
    }
    @Test fun successfulSpeechKeepsExactAudioAndReleasesConnection() {
        val c=LanaHttpCancellation();val conn=Connection()
        assertArrayEquals(byteArrayOf(1,2,3),request(c){conn})
        assertEquals(1,conn.opens);assertEquals(1,conn.closes)
        c.cancel();assertEquals(1,conn.closes)
    }
}
