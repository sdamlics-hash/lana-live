package ai.dreibus.lanalive

import org.json.JSONObject

/** Portable user data only. No preferences, endpoints, tokens or implicit consent. */
internal object LanaMemoryBackup {
    const val MAX_BYTES = 8_000_000
    data class Snapshot(val records: String, val tasks: Int, val notes: Int)
    fun validate(records: String): Snapshot {
        require(records.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val root = JSONObject(records)
        require(root.keys().asSequence().toSet() == setOf("tasks", "notes"))
        val tasks = root.getJSONArray("tasks")
        val notes = root.getJSONArray("notes")
        require(tasks.length() <= 100 && notes.length() <= 100)
        val ids = mutableSetOf<String>()
        for (i in 0 until tasks.length()) {
            val row = tasks.getJSONObject(i)
            require(row.keys().asSequence().toSet() == setOf("id", "title", "done"))
            require(row.get("id") is String && row.getString("id").isNotBlank() && row.getString("id").length <= 100 && ids.add(row.getString("id")))
            require(row.get("title") is String && row.getString("title").isNotBlank() && row.getString("title").length <= 3000)
            require(row.get("done") is Boolean)
        }
        for (i in 0 until notes.length()) require(notes.get(i) is String && notes.getString(i).isNotBlank() && notes.getString(i).length <= 3000)
        return Snapshot(root.toString(), tasks.length(), notes.length())
    }
    fun encode(records: String): String {
        val checked = validate(records)
        return JSONObject().put("format", "LANA_MEMORY").put("version", 1)
            .put("createdAt", System.currentTimeMillis()).put("records", JSONObject(checked.records)).toString()
    }
    fun decode(raw: ByteArray): Snapshot {
        require(raw.size <= MAX_BYTES)
        val root = JSONObject(String(raw, Charsets.UTF_8))
        require(root.getString("format") == "LANA_MEMORY" && root.getInt("version") == 1)
        require(root.keys().asSequence().toSet() == setOf("format", "version", "createdAt", "records"))
        return validate(root.getJSONObject("records").toString())
    }
}
