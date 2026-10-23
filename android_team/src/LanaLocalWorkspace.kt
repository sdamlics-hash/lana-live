package ai.dreibus.lanalive

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** Explicit local intents. Notes are never automatically selected for API context. */
internal class LanaLocalWorkspace(raw: String, private val write: (String) -> Boolean) {
    private val root = JSONObject(raw)
    private val tasks = root.getJSONArray("tasks")
    private val notes = root.getJSONArray("notes")
    init {
        require(tasks.length() <= 100 && notes.length() <= 100)
        for (i in 0 until tasks.length()) {
            val row = tasks.getJSONObject(i)
            require(row.getString("title").isNotBlank() && row.getString("title").length <= 3000)
            row.getString("id"); row.getBoolean("done")
        }
        for (i in 0 until notes.length()) require(notes.getString(i).length in 1..3000)
    }
    private fun save(): String? = if (write(root.toString())) null else "Не удалось сохранить изменение. Проверь память во вкладке приложения."
    fun handle(words: String): String? {
        val original = normalize(words)
        val q = original.lowercase(Locale.ROOT).replace("почитай", "прочитай").replace("зачитай", "прочитай").trimEnd('.', '?', '!')
        val note = Regex("^(?:запомни|сохрани заметку)\\s*[:,—-]?\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(original)?.groupValues?.get(1)?.trim()
        val task = Regex("^(?:добавь задачу|создай задачу|запиши задачу)\\s*[:,—-]?\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(original)?.groupValues?.get(1)?.trim()
        if (note != null || task != null) {
            val value = note ?: task!!
            if (value.length > 3000) return "Запись слишком длинная: максимум три тысячи символов."
            val list = if (note != null) notes else tasks
            if (list.length() >= 100) return "В разделе уже сто записей. Новая запись не добавлена."
            for (i in 0 until list.length()) {
                val existing = if (note != null) list.getString(i) else list.getJSONObject(i).getString("title")
                if (existing.equals(value, ignoreCase = true)) return "Такая запись уже сохранена. Дубликат не добавляю."
            }
            if (note != null) notes.put(value) else tasks.put(JSONObject().put("id", UUID.randomUUID().toString()).put("title", value).put("done", false))
            return save() ?: if (note != null) "Запомнила на этом планшете. Заметка не передаётся помощникам без твоего выбора." else "Задача сохранена на этом планшете."
        }
        if (q in setOf("какие у меня задачи", "прочитай мои задачи", "покажи мои задачи")) {
            val active = (0 until tasks.length()).map { tasks.getJSONObject(it) }.filter { !it.getBoolean("done") }
            return if (active.isEmpty()) "Незавершённых задач нет." else "Незавершённых задач: ${active.size}. " + active.take(10).mapIndexed { i, row -> "${i + 1}. ${row.getString("title")}" }.joinToString(". ")
        }
        if (q in setOf("что ты запомнила", "прочитай мои заметки", "прочитай память")) {
            return if (notes.length() == 0) "Сохранённых заметок пока нет." else "Последние заметки: " + (maxOf(0, notes.length() - 5) until notes.length()).map { notes.getString(it) }.joinToString(". ")
        }
        val numberWords = mapOf("один" to "1", "одну" to "1", "два" to "2", "две" to "2", "три" to "3", "четыре" to "4", "пять" to "5", "шесть" to "6", "семь" to "7", "восемь" to "8", "девять" to "9", "десять" to "10")
        val numbered = q.split(" ").let { parts -> parts.dropLast(1).plus(numberWords[parts.last()] ?: parts.last()).joinToString(" ") }
        val done = Regex("^(?:заверши задачу|задача выполнена)\\s+(?:номер\\s+)?(\\d+)$").matchEntire(numbered)?.groupValues?.get(1)?.toIntOrNull()
        if (done != null) {
            val active = (0 until tasks.length()).map { tasks.getJSONObject(it) }.filter { !it.getBoolean("done") }
            if (done !in 1..active.size) return "Задачи с таким номером нет. Сначала попроси прочитать мои задачи."
            val row = active[done - 1]
            row.put("done", true)
            return save() ?: "Отметила выполненной задачу: ${row.getString("title")}"
        }
        val allTasks = (0 until tasks.length()).map { tasks.getJSONObject(it) }
        val completed = allTasks.filter { it.getBoolean("done") }
        if (q == "следующая задача") return allTasks.firstOrNull { !it.getBoolean("done") }?.let { "Следующая задача: ${it.getString("title")}" } ?: "Незавершённых задач нет."
        if (q == "сколько у меня заметок") return "Сохранённых заметок: ${notes.length()}."
        if (q in setOf("прочитай выполненные задачи", "покажи выполненные задачи"))
            return if (completed.isEmpty()) "Выполненных задач нет." else completed.take(10).mapIndexed { i, row -> "${i+1}. ${row.getString("title")}" }.joinToString(". ")
        if (q in setOf("сколько у меня задач", "сводка задач"))
            return "Всего задач: ${tasks.length()}. Незавершённых: ${tasks.length()-completed.size}. Выполненных: ${completed.size}. Заметок: ${notes.length()}."
        if (q in setOf("прочитай последнюю заметку", "последняя заметка"))
            return if (notes.length()==0) "Сохранённых заметок пока нет." else notes.getString(notes.length()-1)
        if (q in setOf("прочитай все заметки", "покажи мои заметки"))
            return if (notes.length()==0) "Сохранённых заметок пока нет." else "Заметок: ${notes.length()}. Первые двадцать: " + (0 until notes.length()).take(20).joinToString(". ") { "${it+1}. ${notes.getString(it)}" }
        if (q in setOf("помощь", "что ты умеешь", "какие команды ты знаешь"))
            return "Могу сохранить заметку, добавить и прочитать задачи, завершить или вернуть задачу, найти запись. Скажи: запомни и текст; добавь задачу и текст; прочитай мои задачи; заверши задачу номер один; передай команде задание и текст. Команда работает через API-помощника; текущий чат ChatGPT, Claude и Manus не подключены."
        val restore = Regex("^верни задачу (?:номер )?(\\d+)$").matchEntire(numbered)?.groupValues?.get(1)?.toIntOrNull()
        if (restore != null) {
            if (restore !in 1..completed.size) return "Выполненной задачи с таким номером нет. Попроси прочитать выполненные задачи."
            val row = completed[restore-1]; row.put("done", false)
            return save() ?: "Вернула в работу задачу: ${row.getString("title")}"
        }
        val find = Regex("^найди (задачу|заметку)\\s+(.+)$").matchEntire(q)
        if (find != null) {
            val needle = find.groupValues[2]
            val found = if (find.groupValues[1]=="задачу") allTasks.map { it.getString("title") }.filter { it.contains(needle, true) }
                else (0 until notes.length()).map { notes.getString(it) }.filter { it.contains(needle, true) }
            return if (found.isEmpty()) "Совпадений не найдено." else "Найдено: ${found.size}. " + found.take(10).joinToString(". ")
        }
        val edit = Regex("^измени (задачу|заметку) (?:номер )?(\\d+)\\s*[:—-]\\s*(.+)$", RegexOption.IGNORE_CASE).matchEntire(original)
        if (edit != null) {
            val n = edit.groupValues[2].toIntOrNull() ?: return "Неверный номер."
            val value = edit.groupValues[3].trim()
            if (value.isBlank() || value.length>3000) return "Новый текст должен содержать от одного до трёх тысяч символов."
            if (edit.groupValues[1].equals("заметку", true)) {
                if (n !in 1..notes.length()) return "Заметки с таким номером нет."
                notes.put(n-1,value)
            } else {
                val active = allTasks.filter { !it.getBoolean("done") }
                if (n !in 1..active.size) return "Незавершённой задачи с таким номером нет."
                active[n-1].put("title",value)
            }
            return save() ?: "Изменение сохранено на планшете."
        }
        return null
    }
    companion object {
        private fun normalize(words: String): String = words.trim().replace(Regex("\\s+"), " ").replace(Regex("^лана[, ]+", RegexOption.IGNORE_CASE), "").replace(Regex("^пожалуйста[, ]+", RegexOption.IGNORE_CASE), "")

        fun matches(words: String): Boolean {
            val q = normalize(words).lowercase(Locale.ROOT).replace("почитай", "прочитай").replace("зачитай", "прочитай").trim().replace(Regex("^лана[, ]+"), "")
            return listOf("запомни ", "запомни:", "сохрани заметку", "добавь задачу", "создай задачу", "запиши задачу", "заверши задачу", "задача выполнена", "верни задачу", "найди задачу", "найди заметку", "измени задачу", "измени заметку").any { q.startsWith(it) } || q.trimEnd('.', '?', '!') in setOf("какие у меня задачи", "прочитай мои задачи", "покажи мои задачи", "что ты запомнила", "прочитай мои заметки", "прочитай память", "прочитай выполненные задачи", "покажи выполненные задачи", "сколько у меня задач", "сводка задач", "прочитай последнюю заметку", "последняя заметка", "прочитай все заметки", "покажи мои заметки", "помощь", "что ты умеешь", "какие команды ты знаешь", "следующая задача", "сколько у меня заметок")
        }
    }
}
