package ai.dreibus.lanalive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class LanaHttpBodyTest {
    @Test fun exactBoundaryAcceptedAndOneByteTooManyRejected() {
        assertEquals("1234", LanaHttpBody.readUtf8Bounded(
            ByteArrayInputStream("1234".toByteArray()), 4))
        try {
            LanaHttpBody.readUtf8Bounded(
                ByteArrayInputStream("12345".toByteArray()), 4)
            fail("Must reject response exceeding 4 bytes")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("слишком большой"))
        }
    }

    @Test fun multibyteRussianTextUsesByteLimitNotCharacterCount() {
        val russian = "Привет, Лана"
        val bytes = russian.toByteArray(Charsets.UTF_8)
        assertEquals(russian, LanaHttpBody.readUtf8Bounded(
            ByteArrayInputStream(bytes), bytes.size))
        try {
            LanaHttpBody.readUtf8Bounded(ByteArrayInputStream(bytes), bytes.size - 1)
            fail("Must reject truncated multibyte response")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("слишком большой"))
        }
    }

    @Test fun invalidUtf8FailsClosed() {
        val malformed = byteArrayOf(0xC3.toByte(), 0x28.toByte())
        try {
            LanaHttpBody.readUtf8Bounded(ByteArrayInputStream(malformed), 4)
            fail("Must reject malformed UTF-8")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("UTF-8"))
        }
    }

    @Test fun neverReadsUnboundedBodyEvenWithoutContentLength() {
        var bytesRead = 0
        val neverEndingStream = object : InputStream() {
            override fun read(): Int {
                bytesRead++
                return 65
            }
        }
        try {
            LanaHttpBody.readUtf8Bounded(neverEndingStream, 1024)
            fail("Must stop at 1025 bytes")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("слишком большой"))
        }
        assertEquals(1025, bytesRead)
    }

    @Test fun emptyBodyAndInvalidLimitAreHandled() {
        assertEquals("", LanaHttpBody.readUtf8Bounded(ByteArrayInputStream(byteArrayOf()), 4))
        try {
            LanaHttpBody.readUtf8Bounded(ByteArrayInputStream(byteArrayOf()), 0)
            fail("Invalid limits must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test fun zeroProgressStreamFailsInsteadOfLoopingForever() {
        val stuck = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        try {
            LanaHttpBody.readUtf8Bounded(stuck, 8)
            fail("No progress must not loop")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("Не удалось прочитать"))
        }
    }
}
