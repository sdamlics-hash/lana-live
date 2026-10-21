package ai.dreibus.lanalive

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Read-only status client for LANA's core. Use on a background thread.
 * The token is not saved, printed or attached to crash diagnostics.
 * HTTPS trust validation is never bypassed.
 */
internal object LanaCoreClient {
    fun checkStatus(baseUrl: String, token: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(token.length >= 32) { "Нужен ключ доступа длиной не менее 32 символов" }
        val base = URL(trimmed)
        require(base.protocol.equals("https", ignoreCase = true) &&
            base.host.isNotBlank() && base.userInfo == null &&
            base.query == null && base.ref == null) {
            "Адрес сервера должен начинаться с https:// и не содержать учётных данных"
        }
        val conn = URL("$trimmed/v1/status").openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 7_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        try {
            val code = conn.responseCode
            if (code == 401 || code == 403) return "Сервер отклонил ключ доступа (HTTP $code)"
            if (code != 200) return "Сервер ответил HTTP $code. Задания не запускались"
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            require(body.length <= 8_192) { "Неожиданно большой ответ сервера" }
            val json = JSONObject(body)
            return if (json.optBoolean("ok", false) &&
                json.optString("mode") == "offline-prototype") {
                "Связь есть. Сервер работает в тестовом режиме."
            } else {
                "Сервер ответил, но режим не подтверждён"
            }
        } finally {
            conn.disconnect()
        }
    }
}
