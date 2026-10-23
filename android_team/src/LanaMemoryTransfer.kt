package ai.dreibus.lanalive

import android.app.Activity
import android.app.AlertDialog
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

internal class LanaMemoryTransfer(private val activity: MainActivity) {
    private val prefs = activity.getSharedPreferences("lana_workspace_v1", Activity.MODE_PRIVATE)
    private var exporting: ByteArray? = null
    private fun notice(text: String) { if (!activity.isDestroyed) Toast.makeText(activity, text, Toast.LENGTH_LONG).show() }
    private fun current() = prefs.getString("records", "{\"tasks\":[],\"notes\":[]}")!!
    private val export = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val bytes = exporting
        exporting = null
        if (uri != null && bytes != null) Thread {
            val success = runCatching { activity.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) } }.isSuccess
            activity.runOnUiThread { notice(if (success) "Копия задач и заметок сохранена" else "Не удалось записать копию. Исходная память сохранена.") }
        }.start()
    }
    private val restore = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Thread {
            val snapshot = runCatching {
                val bytes = activity.contentResolver.openInputStream(uri)!!.use { stream ->
                    val out = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (true) {
                        val n = stream.read(chunk)
                        if (n < 0) break
                        require(out.size() + n <= LanaMemoryBackup.MAX_BYTES)
                        out.write(chunk, 0, n)
                    }
                    out.toByteArray()
                }
                LanaMemoryBackup.decode(bytes)
            }.getOrNull()
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    if (snapshot == null) notice("Файл повреждён, слишком большой или имеет другой формат. Память не изменена.")
                    else AlertDialog.Builder(activity).setTitle("Восстановить память?")
                        .setMessage("В копии задач: ${snapshot.tasks}, заметок: ${snapshot.notes}. Текущие задачи и заметки будут заменены. Журнал команды и подключение сохранятся. Перед заменой сохрани копию текущих данных.")
                        .setNegativeButton("Отмена", null).setPositiveButton("Заменить память") { _, _ ->
                            if (activity.memoryTransferAllowed()) {
                                if (prefs.edit().putString("records", snapshot.records).commit()) {
                                    LanaWorkspace.clearContextSelection()
                                    activity.recreate()
                                } else notice("Не удалось сохранить память. Восстановление не подтверждено.")
                            } else notice("Сначала останови разговор и дождись ответа команды.")
                        }.show()
                }
            }
        }.start()
    }
    fun mount(panel: LinearLayout) {
        panel.addView(Button(activity).apply {
            text = "Сохранить копию задач и заметок"
            setOnClickListener {
                try {
                    exporting = LanaMemoryBackup.encode(current()).toByteArray(Charsets.UTF_8)
                    export.launch("LANA-memory-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())}.json")
                } catch (_: Exception) { exporting = null; notice("Память не прочитана. Копия не создана.") }
            }
        })
        panel.addView(Button(activity).apply {
            text = "Восстановить задачи и заметки из копии"
            setOnClickListener {
                if (activity.memoryTransferAllowed()) restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                else notice("Сначала останови разговор и дождись ответа команды.")
            }
        })
    }
}
