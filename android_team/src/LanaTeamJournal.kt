package ai.dreibus.lanalive

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Commit before dispatch; interrupted requests are never automatically resent. */
internal class LanaTeamJournal(raw: String, private val write: (String) -> Boolean) {
    private var entries = JSONArray(raw)
    init {
        require(raw.length <= 8_000_000 && entries.length() <= 1000)
        val ids = mutableSetOf<String>()
        var interrupted = false
        for (i in 0 until entries.length()) {
            val row = entries.getJSONObject(i)
            require(ids.add(row.getString("id")))
            require(row.getString("task").length in 1..1800)
            require(row.getString("status") in setOf("running", "review", "verified", "error", "interrupted", "rejected"))
            require(row.optString("answer").length <= 5000)
            if (row.getString("status") == "running") {
                row.put("status", "interrupted")
                interrupted = true
            }
        }
        if (interrupted) check(write(entries.toString())) { "Journal unavailable" }
    }
    fun records() = JSONArray(entries.toString())
    fun filtered(query: String, status: String, since: Long = 0, oldestFirst: Boolean = false, archived: Boolean = false): JSONArray {
        val result = JSONArray()
        val needle = query.trim().lowercase(java.util.Locale.ROOT)
        for (i in if (oldestFirst) (0 until entries.length()) else (entries.length() - 1 downTo 0)) {
            val row = entries.getJSONObject(i)
            val text = (row.getString("task") + " " + row.optString("answer") + " " + row.optString("reviewNote")).lowercase(java.util.Locale.ROOT)
            if (row.optBoolean("archived", false) == archived && row.optLong("createdAt") >= since && (status.isEmpty() || row.getString("status") == status) && text.contains(needle))
                result.put(JSONObject(row.toString()))
        }
        return result
    }
    fun archive(id: String, archived: Boolean) {
        val next = records()
        val row = (0 until next.length()).map { next.getJSONObject(it) }.single { it.getString("id") == id }
        check(row.getString("status") != "running")
        row.put("archived", archived)
        commit(next)
    }
    fun reject(id: String, reason: String) {
        require(reason.isNotBlank() && reason.length <= 500)
        val next = records()
        val row = (0 until next.length()).map { next.getJSONObject(it) }.single { it.getString("id") == id }
        check(row.getString("status") == "review")
        row.put("status", "rejected").put("reviewNote", reason).put("reviewedAt", System.currentTimeMillis())
        commit(next)
    }
    private fun commit(next: JSONArray) {
        require(next.toString().length <= 8_000_000)
        check(write(next.toString())) { "Journal unavailable" }
        entries = next
    }
    fun begin(task: String): String {
        require(task.isNotBlank() && task.length <= 1800 && entries.length() < 1000)
        for (i in 0 until entries.length()) {
            val row = entries.getJSONObject(i)
            require(row.getString("task") != task) { "Duplicate task" }
            check(row.getString("status") != "running") { "Another task is running" }
        }
        val id = UUID.randomUUID().toString()
        val next = records()
        next.put(JSONObject().put("id", id).put("task", task).put("status", "running")
            .put("createdAt", System.currentTimeMillis()).put("executor", "OpenAI"))
        commit(next)
        return id
    }
    fun finish(id: String, answer: String?, error: String?) {
        require((answer != null) != (error != null))
        val next = records()
        val row = (0 until next.length()).map { next.getJSONObject(it) }.single { it.getString("id") == id }
        check(row.getString("status") == "running")
        if (answer != null) {
            require(answer.isNotBlank() && answer.length <= 5000)
            row.put("answer", answer).put("status", "review")
        } else row.put("error", error!!.take(200)).put("status", "error")
        row.put("finishedAt", System.currentTimeMillis())
        commit(next)
    }
    fun reopen(id: String) {
        val next = records()
        val row = (0 until next.length()).map { next.getJSONObject(it) }.single { it.getString("id") == id }
        val previous = row.getString("status")
        check(previous in setOf("verified", "rejected"))
        val history = row.optJSONArray("reviewHistory") ?: JSONArray()
        history.put(JSONObject().put("status", previous).put("note", row.optString("reviewNote"))
            .put("reviewedAt", row.optLong("reviewedAt")).put("reopenedAt", System.currentTimeMillis()))
        row.put("reviewHistory", history).put("status", "review")
        row.remove("reviewNote")
        row.remove("reviewedAt")
        commit(next)
    }
    fun confirm(id: String) {
        val next = records()
        val row = (0 until next.length()).map { next.getJSONObject(it) }.single { it.getString("id") == id }
        check(row.getString("status") == "review")
        row.put("status", "verified").put("reviewedAt", System.currentTimeMillis())
        commit(next)
    }
}
