package ai.dreibus.lanalive

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Authenticated read-only diagnostics. No billable model request or automatic chat retry. */
internal object LanaCoreClient {
    fun checkStatus(baseUrl: String, token: String): String {
        require(token.length >= 32) { "Нужен действующий ключ доступа" }
        val conn = URL(LanaConnectionStatus.root(baseUrl) + "/v1/connections").openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 7_000
        conn.readTimeout = 8_000
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        val unknown = LanaConnectionStatus.render("unknown", "unknown", "unknown")
        try {
            val code = conn.responseCode
            if (code == 401 || code == 403) return "Сервер отклонил ключ доступа.\n$unknown"
            if (code == 404) return "Сервер не поддерживает подробные статусы. Совместимость не подтверждена.\n$unknown"
            if (code != 200) return "Проверка не завершена (HTTP $code).\n$unknown"
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8193)
                var count = 0
                while (count < buffer.size) {
                    val n = reader.read(buffer, count, buffer.size - count)
                    if (n == -1) break
                    count += n
                }
                require(count <= 8192) { "Ответ сервера слишком большой" }
                String(buffer, 0, count)
            }
            val json = JSONObject(body)
            if (json.optString("schema") != "lana.connections.v1") return "Неизвестный формат статуса.\n$unknown"
            val items = json.optJSONObject("connections")
            fun state(name: String) = items?.optJSONObject(name)?.optString("state", "unknown") ?: "unknown"
            return "Сервер отвечает.\n" + LanaConnectionStatus.render(state("openai_api"), state("chatgpt_history"), state("lana_memory"), state("claude_api"), state("manus_api"))
        } finally { conn.disconnect() }
    }
}
