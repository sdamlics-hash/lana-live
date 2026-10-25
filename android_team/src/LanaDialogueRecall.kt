package ai.dreibus.lanalive

import java.util.Locale

/** Honest, on-device readout of bounded encrypted dialogue; never invokes an AI model. */
internal object LanaDialogueRecall {
    fun matches(words: String): Boolean {
        val q = words.lowercase(Locale.ROOT).replace('ё','е')
            .replace(Regex("[?!.,:]"), " ").replace(Regex("\\s+"), " ").trim()
            .replace(Regex("^(?:лана|ладно)\\s+"), "")
        if (!q.contains("разговор") && !q.contains("бесед") && !q.contains("переписк")) return false
        return q.contains("что ты помнишь") || q.contains("ты помнишь") ||
            q.contains("вспомни") || q.contains("что мы обсуждали") ||
            q.contains("о чем мы говорили")
    }
    fun reply(legacy: Boolean, turns: List<LanaLegacyTurn>, storageWarning: Boolean = false): String {
        if (!legacy) return "В этом режиме у меня нет подтверждённого доступа к прежним репликам. На планшете есть журнал разговора, но полная история ChatGPT мне недоступна. Не стану выдумывать вчерашнюю беседу."
        if (storageWarning) return "Сохранённый локальный контекст сейчас не читается. Не могу подтвердить, что мы обсуждали вчера. Заметки и задачи хранятся отдельно."
        val recent = turns.filter { it.role == "user" }.takeLast(3)
        if (recent.isEmpty()) return "В локальном контексте этого разговора нет сохранённых прошлых реплик. Это не означает, что приложение никогда не сохраняет разговоры. История ChatGPT недоступна."
        val snippets = recent.mapIndexed { index, turn ->
            (index + 1).toString() + ") " + turn.content.replace(Regex("\\s+"), " ").trim().take(125)
        }.joinToString("; ")
        return "В сохранённом контексте этого разговора вижу твои недавние слова: " + snippets +
            ". Даты отдельных реплик здесь нет, поэтому не могу подтвердить, что именно из этого было вчера. Вся история ChatGPT не подключена."
    }
}
