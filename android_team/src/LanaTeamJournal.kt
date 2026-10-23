package ai.dreibus.lanalive

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Commit before dispatch; interrupted requests are never automatically resent. */
internal class LanaTeamJournal(raw: String, private val write: (String) -> Boolean) {
    private var entries = JSONArray(raw)
    init {
        require(entries.length() <= 100)
        val ids = mutableSetOf<String>()
        var interrupted = false
        for (i in 0 until entries.length()) {
            val row = entries.getJSONObject(i)
            require(ids.add(row.getString("id")))
            require(row.getString("task").length in 1..1800)
            require(row.getString("status") in setOf("running", "review", "verified", "error", "interrupted"))
            require(row.optString("answer").length <= 5000)
            if (row.getString("status") == "running") {
                row.put("status", "interrupted")
                interrupted = true
            }
        }
        if (interrupted) check(write(entries.toString())) { "Journal unavailable" }
    }
    fun records() = JSONArray(entries.toString())
    private fun commit(next: JSONArray) {
        check(write(next.toString())) { "Journal unavailable" }
        entries = next
    }
    fun begin(task: String): String {
        require(task.isNotBlank() && task.length <= 1800 && entries.length() < 100)
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
        commit(next)
    }
    fun confirm(id: String) {
        val next = records()
        val row = (0 until next.length()).map { next.getJSONObject(it) }.single { it.getString("id") == id }
        check(row.getString("status") == "review")
        row.put("status", "verified")
        commit(next)
    }
}
