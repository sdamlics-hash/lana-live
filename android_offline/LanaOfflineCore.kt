package ai.dreibus.lanalive

import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Local-only policy: no credential, endpoint, network client or cloud fallback. */
internal class LanaOfflineCore(private val clock: Clock = Clock.systemDefaultZone()) {
    fun answer(words: String): String {
        if (words.length > 4000) return "Вопрос слишком длинный. Раздели его на части."
        val q = words.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
            .trimEnd('.', '!', '?').replace(Regex("^(?:лана|lana)[,: ]+"), "")
            .replace(Regex("[, ]+(?:лана|lana)$"), "").trim()
        if (q.isBlank()) return "Скажи или напиши вопрос."
        val now = ZonedDateTime.now(clock)
        return when (q) {
            "сколько времени", "сколько время", "который час", "какое сейчас время", "сколько сейчас времени", "сколько время сейчас" ->
                "По часам планшета сейчас " + now.format(DateTimeFormatter.ofPattern("HH:mm")) + "."
            "какая сегодня дата", "какое сегодня число", "какой сегодня день" ->
                "Сегодня " + now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu",Locale.forLanguageTag("ru"))) + "."
            "ты тут", "ты здесь", "привет", "доброе утро", "добрый день", "добрый вечер", "здравствуй", "здравствуйте" -> "Я на связи."
            "как дела", "как твои дела", "как твои дела сегодня" -> "Я на связи. Работаю в локальном режиме."
            "ты работаешь без сервера", "режим работы", "ты в интернете" -> "Локальный режим. Запросы в облако не отправляются."
            else -> NEED_MODEL
        }
    }
    companion object {
        const val NEED_MODEL = "Для свободного разговора нужна локальная нейросеть. Она ещё не подключена. В облако ничего не отправлено."
        const val TEAM_DISABLED = "Облачные помощники отключены в локальном режиме. Задание не отправлено."
        fun capability(text: String, original: String?): String? {
            if (original == null) return null
            return if (listOf("OpenAI", "Claude", "Manus", "ChatGPT").any { original.contains(it,true) } &&
                !original.contains("Всей истории",true)) TEAM_DISABLED else original
        }
    }
}
