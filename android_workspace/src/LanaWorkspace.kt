package ai.dreibus.lanalive

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Native local workspace. Existing server/voice settings and history remain untouched. */
internal class LanaWorkspace(private val activity: Activity) {
    private val ink = Color.rgb(52, 44, 42)
    private val coral = Color.rgb(193, 87, 67)
    private val prefs = activity.getSharedPreferences("lana_workspace_v1", Activity.MODE_PRIVATE)
    private var tasks = JSONArray()
    private var notes = JSONArray()
    private var readable = true
    private lateinit var tasksPanel: LinearLayout
    private lateinit var notesPanel: LinearLayout
    private val panels = mutableListOf<View>()
    private val tabs = mutableListOf<Button>()

    private fun text(value: String, size: Float = 16f) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(ink); setPadding(12, 12, 12, 12)
    }
    private fun button(value: String, action: () -> Unit) = Button(activity).apply {
        text = value; isAllCaps = false; setTextColor(coral)
        setOnClickListener { action() }
    }
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun notice(value: String) { Toast.makeText(activity, value, Toast.LENGTH_LONG).show() }
    private fun id(name: String) = activity.resources.getIdentifier(name, "id", activity.packageName)

    private fun read() {
        try {
            val root = JSONObject(prefs.getString("records", "{\"tasks\":[],\"notes\":[]}")!!)
            val loadedTasks = root.getJSONArray("tasks")
            val loadedNotes = root.getJSONArray("notes")
            require(loadedTasks.length() <= 100 && loadedNotes.length() <= 100)
            val identities = mutableSetOf<String>()
            for (i in 0 until loadedTasks.length()) {
                val item = loadedTasks.getJSONObject(i)
                require(item.get("id") is String && identities.add(item.getString("id")))
                require(item.get("title") is String && valid(item.getString("title")))
                require(item.get("done") is Boolean)
            }
            for (i in 0 until loadedNotes.length()) {
                require(loadedNotes.get(i) is String && valid(loadedNotes.getString(i)))
            }
            tasks = loadedTasks; notes = loadedNotes
        } catch (_: Exception) {
            readable = false // Keep original bytes: do not silently overwrite a damaged record.
        }
    }
    private fun valid(value: String) = value.isNotBlank() && value.length <= 3000
    private fun persist(nextTasks: JSONArray, nextNotes: JSONArray): Boolean {
        if (!readable) { notice("Записи не прочитаны. Перезапись заблокирована."); return false }
        return try {
            val body = JSONObject().put("tasks", nextTasks).put("notes", nextNotes).toString()
            if (!prefs.edit().putString("records", body).commit()) {
                notice("Не удалось сохранить. Прежние записи оставлены."); false
            } else { tasks = nextTasks; notes = nextNotes; true }
        } catch (_: Exception) { notice("Не удалось сохранить записи."); false }
    }
    private fun copy(array: JSONArray) = JSONArray(array.toString())
    private fun without(array: JSONArray, index: Int) = JSONArray().also { target ->
        for (i in 0 until array.length()) if (i != index) target.put(array.get(i))
    }
    private fun confirmDeletion(action: () -> Unit) {
        AlertDialog.Builder(activity).setTitle("Удалить запись?")
            .setMessage("Запись будет удалена из памяти этого приложения.")
            .setNegativeButton("Отмена", null).setPositiveButton("Удалить") { _, _ -> action() }.show()
    }
    private fun renderRecords(taskMode: Boolean) {
        val panel = if (taskMode) tasksPanel else notesPanel
        panel.removeAllViews()
        panel.addView(text(if (taskMode) "Твои задачи" else "Память Ланы", 24f))
        panel.addView(text(if (taskMode)
            "Задачи сохранены на этом планшете. Отметка завершения — твоя оценка; автоматического исполнителя здесь пока нет."
            else "Подтверждённые тобой заметки на этом планшете. Они пока не передаются серверному ИИ автоматически. История ChatGPT не подключена."))
        if (!readable) { panel.addView(text("Не удалось прочитать прежние записи. Изменения отключены, чтобы сохранить исходные данные.")); return }
        val input = EditText(activity).apply {
            hint = if (taskMode) "Что нужно сделать?" else "Что Лане нужно помнить?"
            setTextColor(ink); setHintTextColor(Color.DKGRAY); minLines = 2; maxLines = 6
            filters = arrayOf(android.text.InputFilter.LengthFilter(3000))
            contentDescription = if (taskMode) "workspace_task_input" else "workspace_note_input"
        }
        panel.addView(input)
        panel.addView(button(if (taskMode) "Добавить задачу" else "Сохранить заметку") {
            val value = input.text.toString().trim()
            if (!valid(value)) { notice("Введи текст от 1 до 3000 символов."); return@button }
            val records = if (taskMode) tasks else notes
            if (records.length() >= 100) { notice("Лимит — 100 записей в разделе."); return@button }
            for (i in 0 until records.length()) {
                val existing = if (taskMode) records.getJSONObject(i).getString("title") else records.getString(i)
                if (existing == value) { notice("Такая запись уже есть."); return@button }
            }
            val changed = copy(records)
            if (taskMode) changed.put(JSONObject().put("id", UUID.randomUUID().toString()).put("title", value).put("done", false))
            else changed.put(value)
            if (persist(if (taskMode) changed else tasks, if (taskMode) notes else changed)) {
                notice("Сохранено на планшете."); renderRecords(taskMode)
            }
        })
        val records = if (taskMode) tasks else notes
        if (records.length() == 0) panel.addView(text("Записей пока нет."))
        for (i in records.length() - 1 downTo 0) {
            val row = column().apply { setPadding(8, 12, 8, 12) }
            val item = if (taskMode) records.getJSONObject(i) else null
            row.addView(text((if (item?.getBoolean("done") == true) "✓ " else "") +
                (item?.getString("title") ?: records.getString(i))))
            if (taskMode) row.addView(button(if (item!!.getBoolean("done")) "Вернуть в работу" else "Отметить выполненной") {
                val next = copy(tasks)
                next.getJSONObject(i).put("done", !next.getJSONObject(i).getBoolean("done"))
                if (persist(next, notes)) renderRecords(true)
            })
            row.addView(button("Удалить") {
                confirmDeletion {
                    // The list cannot change behind this modal in the UI thread.
                    if (persist(if (taskMode) without(tasks, i) else tasks,
                                if (taskMode) notes else without(notes, i))) renderRecords(taskMode)
                }
            })
            panel.addView(row)
        }
    }

    fun mount() {
        read()
        val host = activity.findViewById<ViewGroup>(android.R.id.content)
        val dialogue = host.getChildAt(0)
        host.removeView(dialogue)
        val shell = column().apply { setBackgroundColor(Color.rgb(250, 244, 236)) }
        val navigation = LinearLayout(activity)
        shell.addView(navigation)
        val body = FrameLayout(activity)
        shell.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        body.addView(dialogue)
        panels.add(dialogue)
        fun addPanel(content: LinearLayout): View {
            val scroll = ScrollView(activity).apply { addView(content) }
            body.addView(scroll); panels.add(scroll); return scroll
        }
        tasksPanel = column(); notesPanel = column()
        addPanel(tasksPanel); addPanel(notesPanel)
        val team = column().apply {
            addView(text("Команда Ланы", 24f))
            addView(text("Лана — главный координатор. Этот экран фиксирует возможности, а не изображает подключённую команду."))
            addView(text("OpenAI: серверный диалог подготовлен; реальное соединение проверяется отдельно.\nChatGPT: прямого доступа к нашей переписке нет.\nClaude: не подключён.\nManus: не подключён.\nАвтоматическая передача заданий и проверка результатов: ещё не подключены."))
        }
        addPanel(team)
        val settings = dialogue.findViewById<LinearLayout>(id("workspaceSettings"))
        (settings.parent as ViewGroup).removeView(settings)
        settings.addView(text("Ключ доступа не сохраняется этим экраном. Микрофон при выключенном экране и режим 24/7 пока не подтверждены."), 0)
        addPanel(settings)
        val names = listOf("Разговор", "Задачи", "Память", "Команда", "Настройки")
        for (index in names.indices) {
            val tab = button(names[index]) {
                if (index == 1 || index == 2) renderRecords(index == 1)
                for (i in panels.indices) panels[i].visibility = if (i == index) View.VISIBLE else View.GONE
                for (i in tabs.indices) tabs[i].isSelected = i == index
            }.apply { contentDescription = "workspace_tab_$index"; textSize = 12f; minWidth = 0; setPadding(4, 8, 4, 8) }
            tabs.add(tab); navigation.addView(tab, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        host.addView(shell)
        for (i in panels.indices) panels[i].visibility = if (i == 0) View.VISIBLE else View.GONE
        tabs[0].isSelected = true
    }
}
