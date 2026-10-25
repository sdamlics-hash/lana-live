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
    private var exportProjectsBytes: ByteArray? = null
    private val exportProjects = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val bytes=exportProjectsBytes; exportProjectsBytes=null
        if(uri != null && bytes != null) Thread {
            val ok=runCatching { activity.contentResolver.openOutputStream(uri,"wt")!!.use { it.write(bytes) } }.isSuccess
            activity.runOnUiThread { notice(if(ok) "Копия памяти проектов сохранена" else "Не удалось экспортировать проекты. Исходные записи сохранены.") }
        }.start()
    }
    private val importProjects = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) Thread {
            val imported=runCatching {
                val bytes=activity.contentResolver.openInputStream(uri)!!.use { stream ->
                    val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                    while(true) {
                        val n=stream.read(buffer); if(n<0) break
                        require(out.size()+n<=LanaProjectMemory.MAX_BYTES+4096)
                        out.write(buffer,0,n)
                    }
                    out.toByteArray()
                }
                LanaProjectMemoryBackup.import(bytes)
            }.getOrNull()
            activity.runOnUiThread {
                if(!activity.isDestroyed && !activity.isFinishing) {
                    if(imported==null) notice("Копия памяти проектов повреждена или несовместима. Память не изменена.")
                    else AlertDialog.Builder(activity).setTitle("Восстановить память проектов?")
                        .setMessage("Внимание: только после твоего подтверждения текущие факты проектов будут заменены копией. Сначала сохрани резервную копию. Задачи, заметки и подключение не затрагиваются.")
                        .setNegativeButton("Отмена",null)
                        .setPositiveButton("Заменить факты") { _,_ ->
                            if(!activity.memoryTransferAllowed()) notice("Останови голос и дождись окончания заданий.")
                            else {
                                val ok=runCatching { LanaProjectMemoryGateway.restore(activity,imported) }.getOrDefault(false)
                                notice(if(ok) "Факты проектов восстановлены" else "Не удалось восстановить проекты. Данные не подтверждены.")
                            }
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
        panel.addView(Button(activity).apply {
            text = "Сохранить отдельную копию памяти проектов"
            setOnClickListener {
                if(!activity.memoryTransferAllowed()) notice("Сначала останови разговор и дождись ответа команды.")
                else try {
                    exportProjectsBytes = LanaProjectMemoryBackup.export(LanaProjectMemoryGateway.snapshot(activity))
                    exportProjects.launch("LANA-projects-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss",java.util.Locale.ROOT).format(java.util.Date()) + ".json")
                } catch (_:Exception) { exportProjectsBytes=null; notice("Не удалось прочитать память проектов. Копия не создана.") }
            }
        })
        panel.addView(Button(activity).apply {
            text = "Восстановить память проектов из копии"
            setOnClickListener {
                if(activity.memoryTransferAllowed()) importProjects.launch(arrayOf("application/json","text/plain","application/octet-stream"))
                else notice("Сначала останови разговор и дождись ответа команды.")
            }
        })
    }
}
