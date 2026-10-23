package ai.dreibus.lanalive

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

internal object LanaConversationControls {
    fun mount(activity: MainActivity, panel: LinearLayout) {
        panel.addView(Button(activity).apply {
            text = "Начать новый разговор без прежнего контекста"
            setOnClickListener {
                if (!activity.memoryTransferAllowed()) {
                    Toast.makeText(activity, "Сначала останови разговор и дождись ответа команды", Toast.LENGTH_LONG).show()
                } else AlertDialog.Builder(activity).setTitle("Новый разговор?")
                    .setMessage("Лана начнёт новую беседу без прежнего контекста. Задачи, заметки и журнал сохранятся. Выбор заметок для передачи будет снят. Это не удаляет прошлые записи с сервера.")
                    .setNegativeButton("Отмена", null).setPositiveButton("Новый разговор") { _, _ ->
                        if (activity.memoryTransferAllowed()) {
                            if (activity.getPreferences(Activity.MODE_PRIVATE).edit().putString("lana_conversation_id", java.util.UUID.randomUUID().toString()).commit()) {
                                LanaWorkspace.clearContextSelection()
                                activity.recreate()
                            } else Toast.makeText(activity, "Не удалось сохранить новый разговор", Toast.LENGTH_LONG).show()
                        }
                    }.show()
            }
        })
        panel.addView(Button(activity).apply {
            text = "Скопировать диагностику голосового сеанса"
            setOnClickListener {
                val clipboard = activity.getSystemService(Activity.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LANA diagnostics", activity.voiceSessionReport()))
                Toast.makeText(activity, "Диагностика скопирована: без текста разговоров и ключей", Toast.LENGTH_LONG).show()
            }
        })
    }
}
