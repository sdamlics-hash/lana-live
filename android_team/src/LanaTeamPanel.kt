package ai.dreibus.lanalive

import android.app.Activity
import android.graphics.Color
import android.widget.*

internal class LanaTeamPanel(private val activity: Activity) {
    fun mount(panel: LinearLayout, send: (String, (String?, String?) -> Unit) -> Unit) {
        val prefs = activity.getSharedPreferences("lana_team_journal_v1", Activity.MODE_PRIVATE)
        fun label(value: String) = TextView(activity).apply {
            text = value; textSize = 16f; setTextColor(Color.rgb(52, 44, 42)); setPadding(12, 12, 12, 12)
        }
        val journal = try {
            LanaTeamJournal(prefs.getString("records", "[]")!!) { prefs.edit().putString("records", it).commit() }
        } catch (_: Exception) {
            panel.addView(label("Журнал не прочитан. Исходные записи сохранены; отправка заблокирована.")); return
        }
        var sending = false
        fun render() {
            panel.removeAllViews()
            panel.addView(label("Лана — координатор команды"))
            panel.addView(label("OpenAI: отправка через существующий сервер Ланы. Это API-помощник, не текущий чат ChatGPT. Результат требует проверки.\nClaude: не подключён.\nManus: не подключён."))
            panel.addView(label("Текст задания и выбранные тобой заметки передаются серверу и OpenAI при нажатии кнопки. Ответ не означает выполнения внешних действий."))
            val input = EditText(activity).apply {
                hint = "Задание помощнику OpenAI"; minLines = 2
                setTextColor(Color.rgb(52, 44, 42))
                filters = arrayOf(android.text.InputFilter.LengthFilter(LanaTeamPrompt.MAX_TASK))
                contentDescription = "team_task_input"
            }
            panel.addView(input)
            panel.addView(Button(activity).apply {
                text = if (sending) "Ожидаю результат…" else "Передать задание OpenAI"
                isEnabled = !sending
                contentDescription = "team_task_send"
                setOnClickListener {
                    val task = input.text.toString().trim()
                    val id = try { journal.begin(task) } catch (_: Exception) {
                        Toast.makeText(activity, "Нужно новое задание от 1 до 1000 символов; повторная отправка заблокирована.", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    sending = true
                    render()
                    send(LanaTeamPrompt.build(task)) { answer, error ->
                        sending = false
                        try { journal.finish(id, answer, error) } catch (_: Exception) {
                            // Keep the received result visible even when durable storage fails.
                            panel.addView(label("Не удалось сохранить результат. Повторно задание не отправлялось.\n" + (answer ?: error.orEmpty())))
                            return@send
                        }
                        render()
                    }
                }
            })
            val records = journal.records()
            for (i in records.length() - 1 downTo 0) {
                val row = records.getJSONObject(i)
                val state = when (row.getString("status")) {
                    "running" -> "Отправлено, ожидаю ответ"
                    "review" -> "Ответ получен — не проверен"
                    "verified" -> "Результат подтверждён пользователем"
                    "interrupted" -> "Сеанс прерван; результат неизвестен, повторной отправки не было"
                    else -> "Ошибка запроса"
                }
                panel.addView(label("OpenAI · $state\n" + row.getString("task") + "\n" + row.optString("answer", row.optString("error"))))
                if (row.getString("status") == "review") panel.addView(Button(activity).apply {
                    text = "Я проверил результат"
                    setOnClickListener {
                        try { journal.confirm(row.getString("id")); render() }
                        catch (_: Exception) { Toast.makeText(activity, "Не удалось сохранить подтверждение", Toast.LENGTH_LONG).show() }
                    }
                })
            }
        }
        render()
    }
}
