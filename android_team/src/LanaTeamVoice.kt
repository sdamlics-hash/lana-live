package ai.dreibus.lanalive

import java.util.Locale

internal object LanaTeamVoice {
    private val dispatch = Regex("^(?:передай|отправь)\\s+(?:задание\\s+)?(?:помощнику\\s+)?(?:openai|open ai|опенай|опен аи|chatgpt|chat gpt|чат gpt|чатгпт|чату gpt|чату джипити|команде|команды)\\b\\s*[:,—-]?\\s*", RegexOption.IGNORE_CASE)
    private fun input(words: String) = words.trim().replace(Regex("^лана[, ]+", RegexOption.IGNORE_CASE), "")
    fun task(words: String): String? {
        val value = input(words)
        val prefix = dispatch.find(value) ?: return null
        val task = value.substring(prefix.range.last + 1)
            .replace(Regex("^задание(?:\\s*[:,—-]\\s*|\\s+|$)", RegexOption.IGNORE_CASE), "").trim()
        return task.takeIf { it.any { letter -> letter.isLetterOrDigit() } }
    }
    fun localReply(words: String): String? {
        val q = words.lowercase(Locale.ROOT).replace('ё', 'е').replace(Regex("[?!.,]"), "").replace(Regex("\\s+"), " ").trim().removePrefix("лана ")
        if (task(words) == null && (dispatch.containsMatchIn(input(words)) || q.startsWith("дай команду")))
            return "Для отправки назови получателя и само задание. Например: передай команде задание, составь план проверки Ланы. Пока ничего не отправлено."
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
    // Share the coordinator's recognition variants with on-screen and background speech.
    fun wantsLastReply(words: String): Boolean = LanaCoordinator.normalized(words)
        .replace("почитай", "прочитай") in setOf(
        "прочитай последний ответ команды", "какой последний ответ команды", "последний ответ команды")
}
