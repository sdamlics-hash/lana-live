package ai.dreibus.lanalive

import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** One authenticated voice-turn request. Never bypasses Android TLS verification. */
internal object LanaChatClient {
    fun ask(baseUrl: String, bearerToken: String, conversationId: String, question: String): String {
        val base = URL(baseUrl.trim().trimEnd('/'))
        require(base.protocol.equals("https", ignoreCase = true) &&
            base.host.isNotBlank() && base.userInfo == null &&
            base.query == null && base.ref == null) {
            "Нужен защищённый адрес HTTPS"
        }
        require(bearerToken.length >= 32) { "Нет действующего ключа Ланы" }
        require(question.isNotBlank() && question.length <= 2400) { "Слишком длинный вопрос" }
        val conn = URL(base.toExternalForm().trimEnd('/') + "/v1/chat").openConnection() as HttpsURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 8_000
        conn.readTimeout = 32_000
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $bearerToken")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Accept", "application/json")
        val json = JSONObject().put("conversation_id", conversationId).put("text", question)
        val body = json.toString().toByteArray(Charsets.UTF_8)
        conn.setFixedLengthStreamingMode(body.size)
        try {
            conn.outputStream.use { it.write(body) }
            val status = conn.responseCode
            if (status == 401 || status == 403) throw IOException("Сервер отклонил ключ доступа")
            if (status != 200) throw IOException("Сервер ИИ временно недоступен (HTTP $status)")
            val result = conn.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
            if (result.length > 12_000) throw IOException("Ответ сервера слишком большой")
            val answer = JSONObject(result).optString("answer", "").trim()
            if (answer.isBlank()) throw IOException("Получен пустой ответ")
            return answer.take(5000)
        } finally {
            conn.disconnect()
        }
    }
}
