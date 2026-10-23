package ai.dreibus.lanalive

import org.json.JSONArray
import org.json.JSONObject

internal object LanaJournalReport {
    fun summary(rows: JSONArray): String {
        val counts = mutableMapOf<String, Int>()
        for (i in 0 until rows.length()) {
            val status = rows.getJSONObject(i).getString("status")
            counts[status] = (counts[status] ?: 0) + 1
        }
        return "Всего: ${rows.length()} · ждут проверки: ${counts["review"] ?: 0} · приняты: ${counts["verified"] ?: 0} · отклонены: ${counts["rejected"] ?: 0} · ошибки: ${counts["error"] ?: 0} · прерваны: ${counts["interrupted"] ?: 0}"
    }
    fun export(rows: JSONArray): String = (0 until rows.length()).joinToString("\n\n────────\n\n") { i ->
        val row = rows.getJSONObject(i)
        "ID: ${row.getString("id")}\nСтатус: ${row.getString("status")}\nСоздано: ${row.optLong("createdAt")}\nЗадание: ${row.getString("task")}\nОтвет: ${row.optString("answer")}\nОшибка: ${row.optString("error")}\nЗамечание: ${row.optString("reviewNote")}"
    }
    fun duration(row: JSONObject): String {
        if (!row.has("finishedAt")) return "Длительность не измерена"
        val elapsed = row.optLong("finishedAt") - row.optLong("createdAt")
        return if (elapsed < 0) "Часы устройства изменились; длительность неизвестна"
        else "От отправки до сохранения ответа: ${elapsed / 1000} сек."
    }
}
