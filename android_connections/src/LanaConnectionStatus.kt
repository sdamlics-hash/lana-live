package ai.dreibus.lanalive

import java.net.URL

/** Status statements describe evidence, never infer connectivity from a stored key. */
internal object LanaConnectionStatus {
    fun render(api: String, history: String, memory: String,
               claude: String = "unknown", manus: String = "unknown"): String {
        val apiLabel = when (api) {
            "verified_recently" -> "подключён на момент проверки: успешный ответ за последние 5 минут"
            "not_configured" -> "не настроен"
            "unavailable" -> "последний запрос завершился ошибкой"
            "test_mode" -> "тестовый режим, связь с OpenAI не подтверждена"
            else -> "не проверен; наличие ключа не подтверждает связь"
        }
        val historyLabel = if (history == "not_connected") "не подключена" else "статус не подтверждён"
        val memoryLabel = when (memory) {
            "readable" -> "чтение работает; запись не проверена"
            "unavailable" -> "недоступна"
            else -> "не проверена"
        }
        fun otherProvider(state: String): String = when (state) {
            "verified_recently" -> "подключение подтверждено свежим ответом"
            "not_configured" -> "не настроен в текущем сервере"
            "unavailable" -> "последняя попытка завершилась ошибкой"
            else -> "подключение не подтверждено"
        }
        return "OpenAI API — $apiLabel\nClaude — ${otherProvider(claude)}\nManus — ${otherProvider(manus)}\nИстория ChatGPT — $historyLabel\nСерверная память Ланы — $memoryLabel\nЛокальные заметки планшета проверяются отдельно при сохранении."
    }

    fun root(address: String): String {
        val value = address.trim().trimEnd('/')
        val url = URL(value)
        require(url.protocol.equals("https", true) && url.host.isNotBlank() &&
            url.userInfo == null && url.query == null && url.ref == null) { "Нужен адрес HTTPS без пароля и параметров" }
        return when {
            value.endsWith("/v1/lana/chat") -> value.removeSuffix("/v1/lana/chat")
            value.endsWith("/v1/chat") -> value.removeSuffix("/v1/chat")
            else -> value
        }
    }
    fun chatUrl(address: String): String {
        val base = root(address)
        return if (address.trim().trimEnd('/').endsWith("/v1/lana/chat")) "$base/v1/lana/chat" else "$base/v1/chat"
    }
}
