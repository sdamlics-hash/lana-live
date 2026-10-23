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
        val original = words.trim().replace(Regex("^лана[, ]+", RegexOption.IGNORE_CASE), "")
        val q = original.lowercase(Locale.ROOT).trimEnd('.', '?', '!')
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
        val done = Regex("^(?:заверши задачу|задача выполнена)\\s+(?:номер\\s+)?(\\d+)$").matchEntire(q)?.groupValues?.get(1)?.toIntOrNull()
        if (done != null) {
            val active = (0 until tasks.length()).map { tasks.getJSONObject(it) }.filter { !it.getBoolean("done") }
            if (done !in 1..active.size) return "Задачи с таким номером нет. Сначала попроси прочитать мои задачи."
            val row = active[done - 1]
            row.put("done", true)
            return save() ?: "Отметила выполненной задачу: ${row.getString("title")}"
        }
        return null
    }
    companion object {
        fun matches(words: String): Boolean {
            val q = words.lowercase(Locale.ROOT).trim().replace(Regex("^лана[, ]+"), "")
            return listOf("запомни ", "запомни:", "сохрани заметку", "добавь задачу", "создай задачу", "запиши задачу", "заверши задачу", "задача выполнена").any { q.startsWith(it) } || q.trimEnd('.', '?', '!') in setOf("какие у меня задачи", "прочитай мои задачи", "покажи мои задачи", "что ты запомнила", "прочитай мои заметки", "прочитай память")
        }
    }
}
