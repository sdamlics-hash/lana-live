package ai.dreibus.lanalive

import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Exactly one authenticated request in the explicitly selected protocol, with no fallback POST. */
internal object LanaChatClient {
    fun ask(baseUrl: String, bearerToken: String, conversationId: String, question: String,
            contextNotes: List<String> = emptyList(), legacyMode: Boolean = false,
            history: List<LanaLegacyTurn> = emptyList()): String {
        val root = LanaConnectionStatus.root(baseUrl) // Validates TLS URL before attaching credentials.
        require(bearerToken.length >= 32) { "Нет действующего ключа Ланы" }
        val payload = LanaChatProtocol.request(conversationId, question, contextNotes, legacyMode, history)
        val target = if (legacyMode) root + "/v1/lana/chat" else LanaConnectionStatus.chatUrl(baseUrl)
        val conn = URL(target).openConnection() as HttpsURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 8_000
        conn.readTimeout = 32_000
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $bearerToken")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Accept", "application/json")
        val body = payload.toString().toByteArray(Charsets.UTF_8)
        conn.setFixedLengthStreamingMode(body.size)
        try {
            conn.outputStream.use { it.write(body) }
            val status = conn.responseCode
            if (status == 401 || status == 403) throw IOException("Сервер отклонил ключ доступа")
            if (status != 200) throw IOException("Сервер ИИ недоступен (HTTP $status)")
            val result = conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(12001)
                var count = 0
                while (count < buffer.size) {
                    val n = reader.read(buffer, count, buffer.size - count)
                    if (n == -1) break
                    count += n
                }
                if (count > 12000) throw IOException("Ответ сервера слишком большой")
                String(buffer, 0, count)
            }
            return LanaChatProtocol.answer(result, legacyMode)
        } finally { conn.disconnect() }
    }
}
