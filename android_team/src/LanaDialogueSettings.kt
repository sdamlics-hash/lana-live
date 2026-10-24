package ai.dreibus.lanalive

import android.content.Context
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

internal object LanaDialogueSettings {
    private fun store(context: Context): LanaDialogueStyleStore {
        val prefs = context.getSharedPreferences("lana_dialogue_style_v1",Context.MODE_PRIVATE)
        return LanaDialogueStyleStore({ prefs.getString("style",null) },{ prefs.edit().putString("style",it).commit() })
    }
    fun prompt(context: Context, question: String): String = LanaDialoguePrompt.build(question,store(context).load().style)
    fun mount(context: Context, panel: LinearLayout) {
        val storage = store(context)
        val loaded = storage.load()
        fun label(text: String) = TextView(context).apply { this.text = text; setTextColor(0xff342c2a.toInt()); textSize = 16f }
        panel.addView(label("Как разговаривает Лана").apply { textSize = 22f })
        panel.addView(label("Единый стиль для обычного и фонового разговора. В отдельном вопросе можно попросить другой формат."))
        fun choice(text: String, description: String, checked: Boolean) = CheckBox(context).apply {
            this.text = text; contentDescription = description; isChecked = checked
            setTextColor(0xff342c2a.toInt()); isSaveEnabled = false
            panel.addView(this)
        }
        val concise = choice("Короткие ответы","dialogue_style_concise",loaded.style.concise)
        val warm = choice("Тёплый тон","dialogue_style_warm",loaded.style.warm)
        val humor = choice("Уместный юмор","dialogue_style_humor",loaded.style.humor)
        val status = label(if (loaded.readable) "Настройки сохраняются только кнопкой ниже." else
            "Прежние настройки не прочитаны. Пока используется обычный тёплый и краткий стиль.").apply { contentDescription = "dialogue_style_status" }
        fun edited() { status.text = "Выбор изменён. Нажми «Сохранить стиль», чтобы применить его." }
        listOf(concise,warm,humor).forEach { it.setOnCheckedChangeListener { _, _ -> edited() } }
        panel.addView(Button(context).apply {
            text = "Сохранить стиль"; contentDescription = "dialogue_style_save"
            setOnClickListener {
                val saved = storage.save(LanaDialogueStyle(concise.isChecked,warm.isChecked,humor.isChecked))
                status.text = if (saved) "Стиль сохранён на этом устройстве. Действует со следующего вопроса."
                    else "Сохранение не подтверждено. После перезапуска выбор может потеряться."
            }
        })
        panel.addView(status)
    }
}
