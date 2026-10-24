package ai.dreibus.lanalive

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** One source of truth for spoken and visible team state. Never implies tool execution. */
internal object LanaCoordinator {
    fun normalized(words: String): String = words.lowercase(Locale.ROOT).replace('ё', 'е')
        .replace(Regex("\\s+"), " ").trim().replace(Regex("^(?:лана|ладно)[, ]+"), "")
        .replace(Regex("^пожалуйста[, ]+"), "").trimEnd('.', '?', '!')
    fun handles(words: String): Boolean {
        val q = normalized(words)
        return q in setOf("статус команды", "сводка команды", "как дела у команды", "что делает команда", "что ждет проверки", "какие ответы ждут проверки", "прочитай последнее задание команды", "последнее задание команды", "прочитай последний ответ команды", "почитай последний ответ команды", "какой последний ответ команды", "последний ответ команды", "сохрани последний ответ команды в память", "добавь задачу из последнего задания команды") ||
            q == "уточни у команды" || q.startsWith("уточни у команды ") || q.startsWith("уточни у команды:") ||
            q == "продолжи с командой" || q.startsWith("продолжи с командой ") || q.startsWith("продолжи с командой:")
    }
    fun memoryCommand(words: String, records: JSONArray): String? {
        val q = normalized(words)
        if (q !in setOf("сохрани последний ответ команды в память", "добавь задачу из последнего задания команды")) return null
        val row = latest(records) ?: throw IllegalArgumentException("Сохранённого ответа команды пока нет.")
        return if (q.startsWith("сохрани")) {
            val note = "Ответ API-помощника; ${state(row)}. " + row.getString("answer")
            require(note.length <= 3000) { "Ответ слишком длинный для заметки. Выбери нужную часть текста; ничего не обрезано." }
            "Запомни: $note"
        } else "Добавь задачу: " + row.getString("task")
    }
    fun latest(records: JSONArray): JSONObject? = (records.length()-1 downTo 0).map { records.getJSONObject(it) }
        .firstOrNull { !it.optBoolean("archived") && it.optString("answer").isNotBlank() }
    fun state(row: JSONObject): String = when(row.getString("status")) {
        "running" -> "ожидаю ответ"
        "review" -> "ответ ожидает проверки"
        "verified" -> "ответ принят; выполнение действий не проверено"
        "rejected" -> "ответ отклонён"
        "interrupted" -> "сеанс прерван; результат неизвестен"
        else -> "ошибка запроса"
    }
    fun summary(records: JSONArray): String {
        val rows=(0 until records.length()).map { records.getJSONObject(it) }.filter { !it.optBoolean("archived") }
        val counts=rows.groupingBy { it.getString("status") }.eachCount()
        return "OpenAI: отправка через сервер, доступность проверяется запросом. " +
            "В работе: ${counts["running"] ?: 0}. Ждут оценки: ${counts["review"] ?: 0}. Приняты: ${counts["verified"] ?: 0}. Отклонены: ${counts["rejected"] ?: 0}. Ошибки: ${counts["error"] ?: 0}. Прерваны: ${counts["interrupted"] ?: 0}. Claude и Manus не подключены."
    }
    fun reply(words: String, records: JSONArray): String? = when(normalized(words)) {
        "статус команды", "сводка команды", "как дела у команды", "что делает команда" -> summary(records)
        "что ждет проверки", "какие ответы ждут проверки" -> {
            val rows=(0 until records.length()).map { records.getJSONObject(it) }.filter { !it.optBoolean("archived") && it.getString("status")=="review" }
            if(rows.isEmpty()) "Ответов, ожидающих твоей оценки, нет." else "Ждут оценки: ${rows.size}. " + rows.takeLast(5).joinToString(". ") { it.getString("task") }
        }
        "прочитай последнее задание команды", "последнее задание команды" -> {
            val row=(records.length()-1 downTo 0).map { records.getJSONObject(it) }.firstOrNull { !it.optBoolean("archived") }
            row?.let { "${state(it)}. Задание: ${it.getString("task")}" } ?: "В журнале команды пока нет заданий."
        }
        "прочитай последний ответ команды", "почитай последний ответ команды", "какой последний ответ команды" ->
            latest(records)?.let { "${state(it)}. Ответ API-помощника: ${it.getString("answer")}" } ?: "Сохранённого ответа команды пока нет."
        else -> null
    }
    fun followUp(words: String, records: JSONArray): String? {
        val original=words.trim().replace(Regex("^лана[, ]+",RegexOption.IGNORE_CASE), "")
        val match=Regex("^(?:уточни у команды|продолжи с командой)\\s*[:,—-]?\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(original) ?: run {
            if (normalized(words) in setOf("уточни у команды", "продолжи с командой"))
                throw IllegalArgumentException("Назови текст уточнения после команды. Ничего не отправлено.")
            return null
        }
        val previous=latest(records) ?: throw IllegalArgumentException("Нет предыдущего ответа команды. Сначала передай команде задание.")
        val task="Уточнение к заданию: ${previous.getString("task")}\nПредыдущий ответ: ${previous.getString("answer")}\nНовый вопрос: ${match.groupValues[1]}"
        require(task.length <= LanaTeamPrompt.MAX_TASK) { "Контекст уточнения длиннее лимита. Скопируй нужную часть ответа в новое задание во вкладке Команда. Ничего не отправлено." }
        return task
    }
}
