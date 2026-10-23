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
        val drafts = activity.getSharedPreferences("lana_team_drafts_v1", Activity.MODE_PRIVATE)
        var draft = drafts.getString("task", "").orEmpty()
        var report = drafts.getString("lastCheck", "").orEmpty()
        val expanded = mutableSetOf<String>()
        var pageSize = 10
        var sending = false
        var query = ""
        var selectedStatus = ""
        var selectedPeriod = 0
        fun copy(value: String) {
            val clipboard = activity.getSystemService(Activity.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LANA", value))
            Toast.makeText(activity, "Скопировано", Toast.LENGTH_SHORT).show()
        }
        fun render() {
            panel.removeAllViews()
            panel.addView(label("Лана — координатор команды"))
            panel.addView(label("OpenAI: отправка через существующий сервер Ланы. Это API-помощник, не текущий чат ChatGPT. Результат требует проверки.\nClaude: не подключён.\nManus: не подключён."))
            panel.addView(label("Текст задания и выбранные тобой заметки передаются серверу и OpenAI при нажатии кнопки. Ответ не означает выполнения внешних действий."))
            panel.addView(Button(activity).apply {
                text = "Проверить компоненты Ланы"
                contentDescription = "lana_self_check"
                setOnClickListener {
                    val file = java.io.File.createTempFile("lana-check-", ".tmp", activity.cacheDir)
                    try {
                        report = LanaSelfCheck.run { value ->
                            file.writeText(value, Charsets.UTF_8)
                            file.readText(Charsets.UTF_8)
                        }
                    } catch (_: Exception) { report = "ОШИБКА · Не удалось создать тестовый файл" }
                    finally { file.delete() }
                    drafts.edit().putString("lastCheck", report).apply()
                    render()
                }
            })
            if (report.isNotEmpty()) {
                panel.addView(label(report))
                panel.addView(Button(activity).apply { text = "Скопировать отчёт проверки"; setOnClickListener { copy(report) } })
            }
            val input = EditText(activity).apply {
                hint = "Задание помощнику OpenAI"; minLines = 2
                setTextColor(Color.rgb(52, 44, 42))
                filters = arrayOf(android.text.InputFilter.LengthFilter(LanaTeamPrompt.MAX_TASK))
                setText(draft)
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        draft = s.toString()
                        drafts.edit().putString("task", draft).apply()
                    }
                    override fun afterTextChanged(s: android.text.Editable?) { }
                })
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
                    draft = ""
                    drafts.edit().remove("task").apply()
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
            val search = EditText(activity).apply {
                hint = "Поиск по заданиям и ответам"; setText(query)
                contentDescription = "team_journal_search"
            }
            panel.addView(search)
            val statuses = listOf("" to "Все", "review" to "Ожидают проверки", "verified" to "Подтверждены", "rejected" to "Отклонены", "error" to "Ошибки", "interrupted" to "Прерваны", "running" to "В работе")
            val filter = Spinner(activity).apply {
                adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, statuses.map { it.second })
                setSelection(statuses.indexOfFirst { it.first == selectedStatus })
                contentDescription = "team_journal_status"
            }
            panel.addView(filter)
            val periods = listOf("За всё время", "За 24 часа", "За 7 дней", "За 30 дней")
            val period = Spinner(activity).apply {
                adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, periods)
                setSelection(selectedPeriod)
            }
            panel.addView(period)
            panel.addView(Button(activity).apply {
                text = "Найти в журнале"
                setOnClickListener { query = search.text.toString(); selectedStatus = statuses[filter.selectedItemPosition].first; selectedPeriod = period.selectedItemPosition; pageSize = 10; render() }
            })
            val days = listOf(0, 1, 7, 30)[selectedPeriod]
            val since = if (days == 0) 0L else System.currentTimeMillis() - days * 86400000L
            val records = journal.filtered(query, selectedStatus, since)
            panel.addView(label("Найдено: " + records.length()))
            for (i in 0 until minOf(records.length(), pageSize)) {
                val row = records.getJSONObject(i)
                val state = when (row.getString("status")) {
                    "running" -> "Отправлено, ожидаю ответ"
                    "review" -> "Ответ получен — не проверен"
                    "verified" -> "Ответ принят пользователем; выполнение действий не проверено"
                    "rejected" -> "Ответ отклонён пользователем"
                    "interrupted" -> "Сеанс прерван; результат неизвестен, повторной отправки не было"
                    else -> "Ошибка запроса"
                }
                val id = row.getString("id")
                val answer = row.optString("answer", row.optString("error"))
                val timestamp = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(row.optLong("createdAt")))
                panel.addView(label("$timestamp · OpenAI · $state\n" + row.getString("task")))
                val answerView = label(answer).apply {
                    setTextIsSelectable(true)
                    if (id !in expanded) { maxLines = 5; ellipsize = android.text.TextUtils.TruncateAt.END }
                }
                panel.addView(answerView)
                panel.addView(Button(activity).apply {
                    text = if (id in expanded) "Свернуть ответ" else "Показать полный ответ"
                    setOnClickListener { if (!expanded.add(id)) expanded.remove(id); render() }
                })
                panel.addView(Button(activity).apply {
                    text = "Скопировать задание и ответ"
                    setOnClickListener { copy(row.getString("task") + "\n\n" + answer) }
                })
                if (row.getString("status") in setOf("verified", "rejected")) panel.addView(Button(activity).apply {
                    text = "Вернуть на проверку"
                    contentDescription = "team_review_reopen"
                    setOnClickListener {
                        try {
                            journal.reopen(row.getString("id"))
                            selectedStatus = "review"
                            render()
                            Toast.makeText(activity, "Ответ возвращён на проверку; повторной отправки нет", Toast.LENGTH_LONG).show()
                        } catch (_: Exception) { Toast.makeText(activity, "Не удалось сохранить изменение отметки", Toast.LENGTH_LONG).show() }
                    }
                })
                if (row.has("reviewNote")) panel.addView(label("Причина: " + row.getString("reviewNote")))
                if (row.getString("status") == "rejected") panel.addView(Button(activity).apply {
                    text = "Подготовить доработку с замечанием"
                    setOnClickListener {
                        val nextTask = "Доработай задание: " + row.getString("task") + "\nЗамечание к прошлому ответу: " + row.optString("reviewNote")
                        if (nextTask.length > LanaTeamPrompt.MAX_TASK) {
                            copy(nextTask)
                            Toast.makeText(activity, "Текст скопирован; сократи его до 1000 символов перед отправкой", Toast.LENGTH_LONG).show()
                        } else {
                            android.app.AlertDialog.Builder(activity)
                                .setTitle("Подготовить задание на доработку?")
                                .setMessage("Исходная запись сохранится. Текст задания и замечание заменят текущий черновик. Отправка — отдельной кнопкой.")
                                .setNegativeButton("Назад", null)
                                .setPositiveButton("Подготовить") { _, _ ->
                                    draft = nextTask
                                    drafts.edit().putString("task", draft).apply()
                                    render()
                                    Toast.makeText(activity, "Черновик доработки готов вверху экрана", Toast.LENGTH_LONG).show()
                                }.show()
                        }
                    }
                })
                if (row.getString("status") == "review") {
                    val reason = EditText(activity).apply {
                        hint = "Причина отклонения ответа"
                        filters = arrayOf(android.text.InputFilter.LengthFilter(500))
                        setText(drafts.getString("reason_" + id, ""))
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                drafts.edit().putString("reason_" + id, s.toString()).apply()
                            }
                            override fun afterTextChanged(s: android.text.Editable?) { }
                        })
                    }
                    panel.addView(reason)
                    panel.addView(Button(activity).apply {
                        text = "Отклонить ответ"
                        setOnClickListener {
                            try {
                                journal.reject(row.getString("id"), reason.text.toString().trim())
                                drafts.edit().remove("reason_" + id).apply()
                                render()
                            }
                            catch (_: Exception) { Toast.makeText(activity, "Укажи причину; отклонение должно сохраниться", Toast.LENGTH_LONG).show() }
                        }
                    })
                }
                if (row.getString("status") == "review") panel.addView(Button(activity).apply {
                    text = "Принять этот ответ"
                    setOnClickListener {
                        android.app.AlertDialog.Builder(activity)
                            .setTitle("Принять ответ помощника?")
                            .setMessage("Эта отметка оценивает ответ. Она не подтверждает выполнение предложенных действий или работу функций на планшете.")
                            .setNegativeButton("Назад", null)
                            .setPositiveButton("Принять ответ") { _, _ ->
                                try { journal.confirm(row.getString("id")); render() }
                                catch (_: Exception) { Toast.makeText(activity, "Не удалось сохранить подтверждение", Toast.LENGTH_LONG).show() }
                            }.show()
                    }
                })
            }
            if (records.length() > pageSize) panel.addView(Button(activity).apply {
                text = "Показать ещё 10 записей"
                setOnClickListener { pageSize += 10; render() }
            })
        }
        render()
    }
}
