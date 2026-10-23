package ai.dreibus.lanalive

import java.util.Locale

internal object LanaTeamVoice {
    private val dispatch = Regex("^(?:передай|отправь)\\s+(?:задание\\s+)?(?:помощнику\\s+)?(?:openai|open ai|опенай|опен аи|команде)\\s*[:,—-]?\\s*(?:задание\\s*[:,—-]?\\s*)?(.+)$", RegexOption.IGNORE_CASE)
    fun task(words: String): String? {
        val input = words.trim().replace(Regex("^лана[, ]+", RegexOption.IGNORE_CASE), "")
        return dispatch.matchEntire(input)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }
    fun localReply(words: String): String? {
        val q = words.lowercase(Locale.ROOT).replace('ё', 'е').replace(Regex("[?!.,]"), "").replace(Regex("\\s+"), " ").trim().removePrefix("лана ")
        return when (q) {
            "ты можешь давать команды чату gpt", "команды можешь давать чату gpt", "можешь дать команду в chatgpt", "ты подключена к chatgpt" ->
                "Текущим чатом ChatGPT я не управляю. Могу передать отдельное задание API-помощнику OpenAI и сохранить ответ в Команде. Скажи: передай команде задание, и затем само задание."
            "кто подключен к команде", "какие помощники подключены", "кто в твоей команде" ->
                "Есть отправка заданий OpenAI через настроенный сервер. Claude и Manus не подключены. Доступность сервера проверяется настоящим запросом."
            "у тебя есть доступ к нашей переписке", "ты видишь всю историю chatgpt" ->
                "Всей истории ChatGPT у меня нет. Я использую текущий разговор, описание проекта и выбранные тобой заметки."
            "ты работаешь с выключенным экраном", "ты работаешь круглосуточно" ->
                "Работа с выключенным экраном и круглосуточный режим пока не подтверждены. Сейчас проверен разговор в открытом приложении."
            else -> null
        }
    }
    fun wantsLastReply(words: String): Boolean = words.lowercase(Locale.ROOT).trim().trimEnd('.', '?', '!') in setOf(
        "прочитай последний ответ команды", "какой последний ответ команды", "лана прочитай последний ответ команды")
}
