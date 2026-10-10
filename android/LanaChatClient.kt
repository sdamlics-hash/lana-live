package ai.dreibus.lanalive

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Network call MUST run off the Android main thread. Never embed provider API keys in APK. */
object LanaChatClient {
    data class Turn(val role: String, val content: String)

    fun reply(endpoint: String, accessToken: String, message: String, history: List<Turn>): String {
        require(endpoint.startsWith("https://")) { "LANA requires HTTPS" }
        require(accessToken.isNotBlank()) { "LANA access token is missing" }
        val conn = (URL(endpoint.trimEnd('/') + "/v1/lana/chat").openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 25000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            val data = JSONObject().put("message", message).put("history", JSONArray().apply {
                history.takeLast(12).forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            }).toString().toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(data) }
            if (conn.responseCode !in 200..299) throw IllegalStateException("Сервер Ланы недоступен (HTTP ${conn.responseCode})")
            val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(response).getString("reply").also { require(it.isNotBlank()) }
        } finally { conn.disconnect() }
    }
}
