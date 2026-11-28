package ai.dreibus.lanalive

import java.net.URL
import java.io.IOException
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

internal object LanaSpeechClient {
    fun audio(baseUrl: String, token: String, text: String,
        cancellation: LanaHttpCancellation = LanaHttpCancellation(),
        connectionFactory: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }): ByteArray {
        cancellation.checkActive()
        require(token.length >= 32) { "Введи ключ доступа Ланы" }
        require(text.isNotBlank() && text.length <= 4096) { "Ответ слишком длинный для голоса" }
        val root = LanaConnectionStatus.root(baseUrl)
        val connection = connectionFactory(URL(root + "/v1/lana/speech"))
        connection.requestMethod = "POST"
        connection.connectTimeout = 8_000
        connection.readTimeout = 45_000
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "audio/mpeg")
        val payload = JSONObject().put("text", text).toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(payload.size)
        try {
            cancellation.bind(connection)
            cancellation.checkActive()
            connection.connect()
            cancellation.checkActive()
            connection.outputStream.use { it.write(payload) }
            cancellation.checkActive()
            val status = connection.responseCode
            cancellation.checkActive()
            when (status) {
                200 -> Unit
                401, 403 -> throw IOException("Сервер голоса отклонил ключ")
                404 -> throw IOException("На сервере ещё не подключён голос Shimmer")
                429 -> throw IOException("Лимит голоса или ответ ещё создаётся")
                else -> throw IOException("Сервер голоса недоступен")
            }
            if (connection.contentType?.substringBefore(';')?.trim() != "audio/mpeg") throw IOException("Сервер вернул неверный формат голоса")
            return connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    cancellation.checkActive()
                    val count = input.read(chunk)
                    cancellation.checkActive()
                    if (count == -1) break
                    if (out.size() + count > 8_000_000) throw IOException("Голосовой ответ слишком большой")
                    out.write(chunk, 0, count)
                }
                if (out.size() == 0) throw IOException("Сервер вернул пустую запись")
                out.toByteArray()
            }
        } finally { cancellation.release(connection); connection.disconnect() }
    }
}
