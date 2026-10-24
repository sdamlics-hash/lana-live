package ai.dreibus.lanalive

import android.app.Activity
import android.graphics.Color
import android.widget.*

internal class LanaTeamPanel(private val activity: Activity) {
    fun mount(panel: LinearLayout, send: (String, (String?, String?) -> Unit) -> Unit) {
        fun label(value: String) = TextView(activity).apply {
            text = value; textSize = 16f; setTextColor(Color.rgb(52, 44, 42)); setPadding(12, 12, 12, 12); setTextIsSelectable(true)
        }
        val journal = try {
            (activity as MainActivity).teamJournal()
        } catch (_: Exception) {
            panel.addView(label("Журнал не прочитан. Исходные записи сохранены; отправка заблокирована.")); return
        }
        val drafts = activity.getSharedPreferences("lana_team_drafts_v1", Activity.MODE_PRIVATE)
        var draft = drafts.getString("task", "").orEmpty()
        var report = drafts.getString("lastCheck", "").orEmpty()
        val expanded = mutableSetOf<String>()
        var pageSize = 10
        var sending = false
        var query = drafts.getString("query", "").orEmpty()
        var selectedStatus = drafts.getString("status", "").orEmpty().takeIf { it in setOf("", "review", "verified", "rejected", "error", "interrupted", "running") } ?: ""
        var selectedPeriod = drafts.getInt("period", 0).coerceIn(0, 3)
        var oldestFirst = drafts.getBoolean("oldest", false)
        var archivedView = drafts.getBoolean("archive", false)
        fun saveFilters() { drafts.edit().putString("query", query).putString("status", selectedStatus).putInt("period", selectedPeriod).putBoolean("oldest", oldestFirst).putBoolean("archive", archivedView).apply() }
        fun copy(value: String) {
            if (value.length > 100_000) {
                Toast.makeText(activity, "Слишком много текста для буфера: сузь период или поиск. Текст не обрезан.", Toast.LENGTH_LONG).show()
                return
            }
            val clipboard = activity.getSystemService(Activity.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LANA", value))
            Toast.makeText(activity, "Скопировано", Toast.LENGTH_SHORT).show()
        }
        fun render() {
            if (activity.isFinishing || activity.isDestroyed) return
            panel.removeAllViews()
            panel.addView(label("Лана — координатор команды"))
            panel.addView(label("OpenAI: отправка через существующий сервер Ланы. Это API-помощник, не текущий чат ChatGPT. Результат требует проверки.\nClaude: не подключён.\nManus: не подключён."))
            panel.addView(label("Текст задания и выбранные тобой заметки передаются серверу и OpenAI при нажатии кнопки. Ответ не означает выполнения внешних действий."))
            panel.addView(Button(activity).apply {
                text = "Проверить компоненты Ланы"
                contentDescription = "lana_self_check"
                setOnClickListener {
                    var file: java.io.File? = null
                    try {
                        val checkFile = java.io.File.createTempFile("lana-check-", ".tmp", activity.cacheDir)
                        file = checkFile
                        report = LanaSelfCheck.run { value ->
                            checkFile.writeText(value, Charsets.UTF_8)
                            checkFile.readText(Charsets.UTF_8)
                        }
                    } catch (_: Exception) { report = "ОШИБКА · Не удалось создать тестовый файл" }
                    finally { file?.delete() }
                    report = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()) + "\n" + report
                    drafts.edit().putString("lastCheck", report).apply()
                    render()
                }
            })
            if (report.isNotEmpty()) {
                panel.addView(label(report))
                panel.addView(Button(activity).apply { text = "Скопировать отчёт проверки"; setOnClickListener { copy(report) } })
            }
            val counter = label("Черновик: ${draft.length}/${LanaTeamPrompt.MAX_TASK} символов · сохраняется на устройстве")
            panel.addView(counter)
            val input = EditText(activity).apply {
                hint = "Задание помощнику OpenAI"; minLines = 2
                setTextColor(Color.rgb(52, 44, 42))
                filters = arrayOf(android.text.InputFilter.LengthFilter(LanaTeamPrompt.MAX_TASK))
                setText(draft)
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        draft = s.toString()
                        counter.text = "Черновик: ${draft.length}/${LanaTeamPrompt.MAX_TASK} символов · сохраняется на устройстве"
                        drafts.edit().putString("task", draft).apply()
                    }
                    override fun afterTextChanged(s: android.text.Editable?) { }
                })
                contentDescription = "team_task_input"
            }
            panel.addView(input)
            panel.addView(Button(activity).apply {
                text = "Очистить черновик"
                setOnClickListener {
                    android.app.AlertDialog.Builder(activity).setTitle("Очистить только черновик?")
                        .setMessage("Задания и ответы в журнале сохранятся.")
                        .setNegativeButton("Назад", null).setPositiveButton("Очистить") { _, _ ->
                            draft = ""; drafts.edit().remove("task").apply(); render()
                        }.show()
                }
            })
            panel.addView(Button(activity).apply {
                text = if (sending) "Ожидаю результат…" else "Передать задание OpenAI"
                isEnabled = !sending
                contentDescription = "team_task_send"
                setOnClickListener {
                    val task = input.text.toString().trim()
                    if (task.isBlank()) { Toast.makeText(activity, "Введи задание перед отправкой", Toast.LENGTH_LONG).show(); return@setOnClickListener }
                    if (journal.records().length() >= 1000) { Toast.makeText(activity, "Достигнут лимит 1000 записей; история сохранена", Toast.LENGTH_LONG).show(); return@setOnClickListener }
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
                            if (!activity.isFinishing && !activity.isDestroyed)
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
            val oldest = CheckBox(activity).apply { text = "Сначала старые"; isChecked = oldestFirst }
            val archive = CheckBox(activity).apply { text = "Показать архив вместо текущих записей"; isChecked = archivedView }
            panel.addView(oldest); panel.addView(archive)
            panel.addView(Button(activity).apply {
                text = "Найти в журнале"
                setOnClickListener { query = search.text.toString(); selectedStatus = statuses[filter.selectedItemPosition].first; selectedPeriod = period.selectedItemPosition; oldestFirst = oldest.isChecked; archivedView = archive.isChecked; saveFilters(); pageSize = 10; render() }
            })
            panel.addView(Button(activity).apply {
                text = "Сбросить поиск и фильтры"
                setOnClickListener { query = ""; selectedStatus = ""; selectedPeriod = 0; oldestFirst = false; archivedView = false; pageSize = 10; saveFilters(); render() }
            })
            val days = listOf(0, 1, 7, 30)[selectedPeriod]
            val since = if (days == 0) 0L else System.currentTimeMillis() - days * 86400000L
            val records = journal.filtered(query, selectedStatus, since, oldestFirst, archivedView)
            panel.addView(label(LanaJournalReport.summary(journal.records())))
            panel.addView(label("Найдено: ${records.length()} · показано: ${minOf(records.length(), pageSize)} · лимит журнала: 1000"))
            if (records.length() == 0) panel.addView(label("По этим фильтрам записей нет. Сбрось фильтры или переключи архив; записи не удаляются при поиске."))
            panel.addView(Button(activity).apply {
                text = "Скопировать найденные записи"
                isEnabled = records.length() > 0
                setOnClickListener { copy(LanaJournalReport.export(records)) }
            })
            panel.addView(Button(activity).apply {
                text = "Скопировать весь журнал, включая архив"
                isEnabled = journal.records().length() > 0
                setOnClickListener {
                    android.app.AlertDialog.Builder(activity).setTitle("Скопировать весь журнал?")
                        .setMessage("В буфер попадут задания, ответы и замечания, включая архив. Вставляй их только туда, куда хочешь передать эти данные.")
                        .setNegativeButton("Назад", null).setPositiveButton("Скопировать") { _, _ -> copy(LanaJournalReport.export(journal.records())) }.show()
                }
            })
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
                panel.addView(label("ID: $id\n" + LanaJournalReport.duration(row)))
                if (row.getString("status") == "error" || row.getString("status") == "interrupted") panel.addView(label("Проверь подключение в настройках. Автоматической повторной отправки не было; статус не доказывает выполнение задания."))
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
                if (row.getString("status") != "running") panel.addView(Button(activity).apply {
                    text = if (row.optBoolean("archived", false)) "Вернуть из архива" else "Убрать в архив"
                    setOnClickListener {
                        try { journal.archive(id, !row.optBoolean("archived", false)); render() }
                        catch (_: Exception) { Toast.makeText(activity, "Архивирование не сохранено", Toast.LENGTH_LONG).show() }
                    }
                })
                if (row.optJSONArray("reviewHistory") != null) panel.addView(Button(activity).apply {
                    text = "История изменения отметок"
                    setOnClickListener {
                        val history = row.getJSONArray("reviewHistory")
                        val text = (0 until history.length()).joinToString("\n") { index ->
                            val old = history.getJSONObject(index)
                            "${index + 1}. ${old.optString("status")}: ${old.optString("note")}"
                        }
                        android.app.AlertDialog.Builder(activity).setTitle("Предыдущие отметки").setMessage(text).setPositiveButton("Закрыть", null).show()
                    }
                })
                if (row.getString("status") in setOf("verified", "rejected")) panel.addView(Button(activity).apply {
                    text = "Вернуть на проверку"
                    contentDescription = "team_review_reopen"
                    setOnClickListener {
                        try {
                            journal.reopen(row.getString("id"))
                            selectedStatus = "review"; selectedPeriod = 0; query = ""; saveFilters()
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
        (activity as MainActivity).onTeamJournalChanged = { render() }
        render()
    }
}
