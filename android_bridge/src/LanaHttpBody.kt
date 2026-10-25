package ai.dreibus.lanalive

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Reads at most [maxBytes] from an authenticated HTTPS response.
 *
 * A server may omit Content-Length or lie about it. Limit the bytes *while*
 * streaming, before decoding or allocating the full response. Reject malformed
 * UTF-8 instead of silently replacing bytes in JSON.
 */
internal object LanaHttpBody {
    fun readUtf8Bounded(input: InputStream, maxBytes: Int): String {
        require(maxBytes in 1..1_048_576) { "Invalid response size limit" }
        val out = ByteArrayOutputStream(minOf(maxBytes, 4096))
        val buffer = ByteArray(4096)
        while (true) {
            val toRead = minOf(buffer.size, maxBytes - out.size() + 1)
            val count = input.read(buffer, 0, toRead)
            if (count == -1) break
            if (count == 0) throw IOException("Не удалось прочитать ответ сервера")
            out.write(buffer, 0, count)
            if (out.size() > maxBytes) throw IOException("Ответ сервера слишком большой")
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(out.toByteArray()))
                .toString()
        } catch (_: CharacterCodingException) {
            throw IOException("Ответ сервера содержит неверный UTF-8")
        }
    }
}
