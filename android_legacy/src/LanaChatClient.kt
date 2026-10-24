package ai.dreibus.lanalive

import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal class LanaChatFailure(message: String) : IOException(message)

/** Only fixed stage labels; never includes URL, credentials, payload or provider error text. */
internal class LanaChatTimeout(val stage: String, cause: java.net.SocketTimeoutException) :
    IOException(when (stage) {
        "connect" -> "Не удалось подключиться к серверу за 8 секунд."
        "upload" -> "Передача вопроса серверу прервалась по времени."
        "headers" -> "Ответ сервера не начался за 32 секунды."
        else -> "Получение ответа сервера прервалось по времени."
    }, cause)

/** A cancellation owns at most one connection; no payload or credential is retained. */
internal class LanaHttpCancellation {
    private val lock = Any()
    @Volatile private var cancelled = false
    private var connection: HttpsURLConnection? = null
    fun checkActive() { if (cancelled) throw java.io.InterruptedIOException("Запрос остановлен") }
    fun bind(value: HttpsURLConnection) {
        synchronized(lock) {
            checkActive()
            check(connection == null)
            connection = value
        }
    }
    fun release(value: HttpsURLConnection) {
        synchronized(lock) { if (connection === value) connection = null }
    }
    fun cancel(close: (HttpsURLConnection) -> Unit = { it.disconnect() }) {
        val active = synchronized(lock) {
            if (cancelled) return
            cancelled = true
            connection.also { connection = null }
        }
        if (active != null) close(active)
    }
}

/** Exactly one authenticated request in the explicitly selected protocol, with no fallback POST. */
internal object LanaChatClient {
    fun ask(baseUrl: String, bearerToken: String, conversationId: String, question: String,
            contextNotes: List<String> = emptyList(), legacyMode: Boolean = false,
            history: List<LanaLegacyTurn> = emptyList(),
            connectionFactory: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
            cancellation: LanaHttpCancellation = LanaHttpCancellation()): String {
        cancellation.checkActive()
        val root = LanaConnectionStatus.root(baseUrl) // Validates TLS URL before attaching credentials.
        require(bearerToken.length >= 32) { "Нет действующего ключа Ланы" }
        val payload = LanaChatProtocol.request(conversationId, question, contextNotes, legacyMode, history)
        val target = if (legacyMode) root + "/v1/lana/chat" else LanaConnectionStatus.chatUrl(baseUrl)
        val conn = connectionFactory(URL(target))
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
        var stage = "connect"
        try {
            cancellation.bind(conn)
            cancellation.checkActive()
            conn.connect()
            cancellation.checkActive()
            stage = "upload"
            conn.outputStream.use { it.write(body) }
            stage = "headers"
            cancellation.checkActive()
            val status = conn.responseCode
            cancellation.checkActive()
            stage = "body"
            if (status == 401 || status == 403) throw LanaChatFailure("Сервер отклонил ключ доступа. Проверь подключение в настройках.")
            if (status == 422) {
                val safeBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val chars = CharArray(12001)
                    var count = 0
                    while (count < chars.size) {
                        val n = reader.read(chars, count, chars.size-count)
                        if (n < 0) break
                        count += n
                    }
                    if (count > 12000) "" else String(chars,0,count)
                }.orEmpty()
                throw LanaChatFailure(LanaChatProtocol.validationFailure(safeBody))
            }
            if (status != 200) throw LanaChatFailure("Сервер ИИ вернул HTTP $status. Ответ не получен.")
            val result = conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(12001)
                var count = 0
                while (count < buffer.size) {
                    val n = reader.read(buffer, count, buffer.size - count)
                    if (n == -1) break
                    count += n
                }
                if (count > 12000) throw LanaChatFailure("Ответ сервера превышает допустимый размер.")
                String(buffer, 0, count)
            }
            cancellation.checkActive()
            return LanaChatProtocol.answer(result, legacyMode)
        } catch (failure: java.net.SocketTimeoutException) {
            throw LanaChatTimeout(stage, failure)
        } finally { cancellation.release(conn); conn.disconnect() }
    }
}
