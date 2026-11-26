package ai.dreibus.lanalive

import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Local-only policy: no credential, endpoint, network client or cloud fallback. */
internal class LanaOfflineCore(private val clock: Clock = Clock.systemDefaultZone()) {
    fun answer(words: String): String {
        if (words.length > 4000) return "Вопрос слишком длинный. Раздели его на части."
        val q = words.trim().lowercase(Locale.ROOT).replace('ё', 'е')
            .replace(Regex("[\\p{P}\\s]+"), " ").trim()
            .replace(Regex("^(?:лана|lana|ладно) "), "")
            .replace(Regex(" (?:лана|lana)$"), "")
            .replace(Regex("^(?:(?:приятно|спасибо|скажи пожалуйста|скажи|пожалуйста) )+"), "")
            .replace(Regex(" пожалуйста$"), "")
        if (q.isBlank()) return "Скажи или напиши вопрос."
        val now = ZonedDateTime.now(clock)
        return when (q) {
            "сколько времени", "сколько время", "который час", "какое сейчас время", "сколько сейчас времени", "сколько время сейчас" ->
                "По часам планшета сейчас " + now.format(DateTimeFormatter.ofPattern("HH:mm")) + "."
            "какая сегодня дата", "какое сегодня число", "какой сегодня день" ->
                "Сегодня " + now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu",Locale.forLanguageTag("ru"))) + "."
            "ты тут", "ты здесь", "ты меня слышишь", "ты слышишь меня", "где ты пропадаешь", "привет", "доброе утро", "добрый день", "добрый вечер", "здравствуй", "здравствуйте" -> "Я на связи."
            "как дела", "как твои дела", "как твои дела сегодня", "как твое настроение" -> "Я на связи. Работаю в локальном режиме."
            "повторяешь все за мной", "повторяешь все за мной да", "зачем ты повторяешь за мной",
            "ты повторяешь за мной", "ты повторяешь мои вопросы" ->
                "Поняла. Ты ждёшь ответа по смыслу. Задай вопрос ещё раз."
            "совсем некорректно говоришь", "ты говоришь некорректно", "ты меня неправильно поняла" ->
                "Я неверно поняла фразу. Скажи, что именно нужно уточнить."
            "что ты на планшете еще можешь посмотреть", "что ты можешь посмотреть на планшете",
            "что ты видишь на планшете", "что ты умеешь на планшете" ->
                "Могу сказать дату и время, прочитать задачи и заметки из Ланы. Экран других приложений сейчас мне недоступен."
            "ты работаешь без сервера", "режим работы", "ты в интернете" -> "Локальный режим. Запросы в облако не отправляются."
            else -> NEED_MODEL
        }
    }
    companion object {
        const val NEED_MODEL = "Для свободного разговора нужна локальная нейросеть. Она ещё не подключена. В облако ничего не отправлено."
        const val REPHRASE = "Не смогла ответить по смыслу. Скажи, пожалуйста, чуть иначе."
        // Limits are expressed in UTF-16 code units. Keep the stored string well formed.
        private fun safePrefix(text: String, max: Int): String {
            if (text.length <= max) return text
            val end = if (text[max - 1].isHighSurrogate() && text[max].isLowSurrogate()) max - 1 else max
            return text.substring(0, end)
        }

        fun visibleModelReply(raw: String): String? {
            var remaining = raw.trim()
            // A complete reasoning block is internal; an incomplete or misplaced block
            // is ambiguous and must never be spoken or committed to history.
            while (remaining.startsWith("<think>", ignoreCase = true)) {
                val close = remaining.indexOf("</think>", startIndex = 7, ignoreCase = true)
                if (close < 0 || remaining.substring(7, close).contains("<think>", true)) return null
                remaining = remaining.substring(close + 8).trimStart()
            }
            if (remaining.isBlank() || remaining.contains("<think>", true) || remaining.contains("</think>", true)) return null
            return safePrefix(remaining, 5000).trimEnd().takeIf { it.isNotEmpty() }
        }

        fun boundedHistory(turns: List<LanaLegacyTurn>): List<LanaLegacyTurn> =
            turns.takeLast(4).chunked(2)
                .filter { it.size == 2 && it[0].role == "user" && it[1].role == "assistant" &&
                    it.all { turn -> turn.content.isNotBlank() } }
                .flatten().map { it.copy(content = safePrefix(it.content, 200)) }

        fun checkedModelReply(question: String, answer: String): String {
            val reply = answer.trim()
            fun normalized(value: String) = value.lowercase(Locale.ROOT).replace('ё', 'е')
                .replace(Regex("[\\p{P}\\s]+"), " ").trim()
            val plain = normalized(reply)
            val prompt = normalized(question).removePrefix("лана ").removePrefix("пожалуйста ")
            val tail = prompt.substringAfter(' ', "")
            val droppedQuestionWord = prompt.substringBefore(' ') in setOf("что", "как", "где", "почему", "зачем", "когда")
            val strippedEcho = droppedQuestionWord && tail.count { it == ' ' } >= 3 && plain == tail
            return if (plain == prompt || plain == normalized(question) || strippedEcho ||
                plain == "текущий вопрос" || plain.startsWith("текущий вопрос ") ||
                plain.startsWith("предыдущие реплики как справка")) REPHRASE else reply
        }
        const val TEAM_DISABLED = "Облачные помощники отключены в локальном режиме. Задание не отправлено."
        fun capability(text: String, original: String?): String? {
            if (original == null) return null
            return if (listOf("OpenAI", "Claude", "Manus", "ChatGPT").any { original.contains(it,true) } &&
                !original.contains("Всей истории",true)) TEAM_DISABLED else original
        }
    }
}
