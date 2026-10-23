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
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val input = EditText(activity).apply {
            hint = "Вопрос или команда текстом"
            minLines = 2; filters = arrayOf(android.text.InputFilter.LengthFilter(3000))
            setText(prefs.getString("draft", "")); contentDescription = "dialogue_text_input"
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { prefs.edit().putString("draft", s.toString()).apply() }
                override fun afterTextChanged(s: android.text.Editable?) { }
            })
        }
        box.addView(input)
        box.addView(Button(activity).apply {
            text = "Отправить текст Лане"; contentDescription = "dialogue_text_send"
            setOnClickListener { val value = input.text.toString().trim(); if (value.isNotEmpty() && submit(value)) input.text.clear() }
        })
        val toggle = Button(activity).apply {
            text = "Написать вместо голоса"
            setOnClickListener {
                box.visibility = if (box.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                text = if (box.visibility == View.VISIBLE) "Скрыть текстовый ввод" else "Написать вместо голоса"
            }
        }
        val at = parent.indexOfChild(anchor) + 1
        parent.addView(toggle, at); parent.addView(box, at + 1)
    }
}
