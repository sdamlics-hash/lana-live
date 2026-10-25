package ai.dreibus.lanalive

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout

internal object LanaTypedDialogue {
    fun install(activity: Activity, anchor: Button, submit: (String) -> Boolean) {
        val parent = anchor.parent as? LinearLayout ?: return
        val prefs = activity.getSharedPreferences("lana_typed_dialogue", Activity.MODE_PRIVATE)
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; visibility = if (prefs.getBoolean("expanded", false)) View.VISIBLE else View.GONE }
        val counter = android.widget.TextView(activity)
        val send = Button(activity)
        val input = EditText(activity).apply {
            hint = "Вопрос или команда текстом"
            minLines = 2; filters = arrayOf(android.text.InputFilter.LengthFilter(3000))
            setText(prefs.getString("draft", "")); contentDescription = "dialogue_text_input"
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { prefs.edit().putString("draft", s.toString()).apply(); counter.text = "${s?.length ?: 0}/3000 символов"; send.isEnabled = !s.isNullOrBlank() }
                override fun afterTextChanged(s: android.text.Editable?) { }
            })
        }
        box.addView(android.widget.TextView(activity).apply { text = "Команды выполняются только после нажатия «Отправить». Заметки и задачи сохраняются на планшете." })
        box.addView(input)
        counter.text = "${input.length()}/3000 символов"; box.addView(counter)
        val shortcuts = android.widget.HorizontalScrollView(activity)
        val row = LinearLayout(activity)
        for (sample in listOf("Прочитай мои задачи", "Сводка задач", "Прочитай все заметки", "Покажи проекты памяти", "Запомни для проекта Лана: ", "Что ты помнишь по проекту Лана", "Что ты умеешь", "Передай команде задание: ")) {
            row.addView(Button(activity).apply { text = sample; setOnClickListener { input.setText(sample); input.setSelection(input.length()); input.requestFocus() } })
        }
        shortcuts.addView(row); box.addView(shortcuts)
        box.addView(Button(activity).apply {
            text = "Очистить текст"
            setOnClickListener { if (input.length()>0) android.app.AlertDialog.Builder(activity).setMessage("Очистить сохранённый черновик?").setNegativeButton("Оставить", null).setPositiveButton("Очистить") { _, _ -> input.text.clear() }.show() }
        })
        box.addView(send.apply {
            isEnabled = !input.text.isNullOrBlank()
            text = "Отправить текст Лане"; contentDescription = "dialogue_text_send"
            setOnClickListener { val value = input.text.toString().trim(); if (value.isNotEmpty() && submit(value)) {
                input.text.clear()
                (activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)?.hideSoftInputFromWindow(input.windowToken, 0)
            } }
        })
        val toggle = Button(activity).apply {
            text = if (box.visibility == View.VISIBLE) "Скрыть текстовый ввод" else "Написать вместо голоса"
            setOnClickListener {
                box.visibility = if (box.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                prefs.edit().putBoolean("expanded", box.visibility == View.VISIBLE).apply()
                text = if (box.visibility == View.VISIBLE) "Скрыть текстовый ввод" else "Написать вместо голоса"
            }
        }
        val at = parent.indexOfChild(anchor) + 1
        parent.addView(toggle, at); parent.addView(box, at + 1)
    }
}
