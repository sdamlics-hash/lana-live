package ai.dreibus.lanalive

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Project facts are saved only by an explicit user command; no LLM auto-memory. */
internal class LanaProjectMemory(raw: String, private val write: (String)->Boolean,
    private val clock: ()->Long = { System.currentTimeMillis() }) {
    companion object {
        const val EMPTY = """{"version":2,"facts":[]}"""
        const val MAX_FACTS = 100
        const val MAX_BYTES = 120000
        private val saveCommand=Regex("""^запомни для проекта\s+(.{1,60}?)\s*[:—-]\s*(.+)$""",RegexOption.IGNORE_CASE)
        private val readCommand=Regex("""^(?:что ты помнишь (?:по|о) проекту|прочитай память проекта|покажи память проекта)\s+(.+)$""",RegexOption.IGNORE_CASE)
        private val findCommand=Regex("""^найди в памяти проекта\s+(.{1,60}?)\s*[:—-]\s*(.+)$""",RegexOption.IGNORE_CASE)
        fun clean(value:String):String = value.trim().replace(Regex("""\s+""")," ")
            .replace(Regex("""^лана[, ]+""",RegexOption.IGNORE_CASE),"").trim().trimEnd('.', '?', '!')
        fun matches(text:String):Boolean {
            val q=clean(text)
            return saveCommand.matches(q) || readCommand.matches(q) || findCommand.matches(q)
                || listOf("запомни для проекта","покажи память проекта","прочитай память проекта",
                    "что ты помнишь по проекту","что ты помнишь о проекту","найди в памяти проекта")
                    .any { q.lowercase(Locale.ROOT).startsWith(it) }
                || q.lowercase(Locale.ROOT) in setOf("покажи проекты памяти","какие проекты ты помнишь")
        }
        fun validate(raw:String):String {
            require(raw.toByteArray(Charsets.UTF_8).size<=MAX_BYTES)
            val o=JSONObject(raw)
            require(o.keys().asSequence().toSet()==setOf("version","facts") && o.getInt("version")==2)
            val facts=o.getJSONArray("facts")
            require(facts.length()<=MAX_FACTS)
            val ids=mutableSetOf<String>()
            for(i in 0 until facts.length()) {
                val fact=facts.getJSONObject(i)
                require(fact.keys().asSequence().toSet()==setOf("id","project","text","createdAt","source"))
                require(fact.getString("id").length in 1..80 && ids.add(fact.getString("id")))
                require(fact.getString("project").length in 1..60)
                require(fact.getString("text").length in 1..800)
                require(fact.getLong("createdAt")>0)
                require(fact.getString("source")=="user_explicit")
            }
            return o.toString()
        }
    }
    private var root=JSONObject(validate(raw))
    private var committed=root.toString()
    private val facts get()=root.getJSONArray("facts")
    private fun formatted(timestamp:Long):String =
        SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'",Locale.ROOT).apply { timeZone=TimeZone.getTimeZone("UTC") }.format(Date(timestamp))
    private fun commit():Boolean {
        val candidate=root.toString()
        val ok=try { write(candidate) } catch (_:Exception) { false }
        if(ok) { committed=candidate; return true }
        root=JSONObject(committed); return false
    }
    fun handle(input:String):String? {
        if(!matches(input)) return null
        val q=clean(input)
        saveCommand.matchEntire(q)?.let { m ->
            val project=m.groupValues[1].trim(); val item=m.groupValues[2].trim()
            if(project.length !in 1..60 || item.length !in 1..800)
                return "Название проекта или факт слишком длинные. Ничего не сохранено."
            if(facts.length()>=MAX_FACTS) return "Память проектов заполнена. Ничего не сохранено."
            if((0 until facts.length()).any { i -> facts.getJSONObject(i).let { row ->
                row.getString("project").equals(project,true) && row.getString("text").equals(item,true)
            } }) return "Этот факт уже сохранён для проекта "+project+". Дубликат не создан."
            facts.put(JSONObject().put("id",UUID.randomUUID().toString()).put("project",project)
                .put("text",item).put("createdAt",clock()).put("source","user_explicit"))
            if(!commit()) return "Не удалось сохранить факт. Память не изменена."
            return "Сохранила для проекта "+project+" на этом планшете. Другим ИИ факт не передан."
        }
        readCommand.matchEntire(q)?.let { m ->
            val project=m.groupValues[1].trim()
            val rows=(0 until facts.length()).map { facts.getJSONObject(it) }.filter { it.getString("project").equals(project,true) }
            return if(rows.isEmpty()) "Для проекта "+project+" подтверждённых фактов пока нет."
            else "Проект "+project+", фактов: "+rows.size+". "+
                rows.takeLast(5).mapIndexed { i,r ->
                    (i+1).toString()+". "+r.getString("text")+" (записано "+formatted(r.getLong("createdAt"))+")"
                }.joinToString("; ")
        }
        findCommand.matchEntire(q)?.let { m ->
            val project=m.groupValues[1].trim(); val term=m.groupValues[2].trim()
            val rows=(0 until facts.length()).map { facts.getJSONObject(it) }.filter {
                it.getString("project").equals(project,true) && it.getString("text").contains(term,true)
            }
            return if(rows.isEmpty()) "В памяти проекта "+project+" совпадений нет."
                else "В проекте "+project+" найдено: "+rows.size+". "+rows.take(5).joinToString("; ") { it.getString("text") }
        }
        if(q.lowercase(Locale.ROOT) !in setOf("покажи проекты памяти","какие проекты ты помнишь"))
            return "Команда памяти проекта неполная. Назови проект и текст. Ничего не сохранено."
        val projects=(0 until facts.length()).map { facts.getJSONObject(it).getString("project") }
            .distinctBy { it.lowercase(Locale.ROOT) }
        return if(projects.isEmpty()) "В памяти проектов пока нет подтверждённых фактов."
            else "Проекты с фактами: "+projects.joinToString(", ")
    }
}
