package ai.dreibus.lanalive

import android.content.Context
import org.json.JSONArray

internal object LanaLegacyQuotaPolicy {
    fun retained(times: List<Long>, now: Long): List<Long> {
        require(now > 0 && times.size <= 100 && times.all { it > 0 })
        return times.filter { it > now - 86400000L }
    }
    fun allowed(times: List<Long>, now: Long): Boolean {
        val kept = retained(times, now)
        return kept.size < 100 && kept.count { it > now - 3600000L } < 30
    }
}
/** Device-local attempt limit for the legacy server; not a server-wide monetary guarantee. */
internal object LanaLegacyQuota {
    @Synchronized fun claim(context: Context): String? = try {
        val prefs = context.getSharedPreferences("lana_legacy_usage_v1", Context.MODE_PRIVATE)
        val stored = JSONArray(prefs.getString("attempts", "[]")!!)
        require(stored.length() <= 100)
        val times = (0 until stored.length()).map {
            val raw = stored.get(it)
            require(raw is Long || raw is Int)
            (raw as Number).toLong()
        }
        val now = System.currentTimeMillis()
        if (!LanaLegacyQuotaPolicy.allowed(times, now)) "Достигнут лимит: 30 попыток в час или 100 за сутки."
        else {
            val next = LanaLegacyQuotaPolicy.retained(times, now) + now
            if (prefs.edit().putString("attempts", JSONArray(next).toString()).commit()) null
            else "Не удалось сохранить счётчик запросов. Отправка остановлена."
        }
    } catch (_: Exception) { "Не удалось проверить лимит запросов. Отправка остановлена." }
}
