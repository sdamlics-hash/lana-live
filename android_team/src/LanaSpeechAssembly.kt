package ai.dreibus.lanalive

import java.util.Locale

/** Assembles final Android recognition segments; partial hypotheses never leave the device. */
internal class LanaSpeechAssembly {
    companion object { const val CONTINUATION_MS = 1400L }
    private val segments = mutableListOf<String>()
    private var deadline: Long? = null
    var continuing = false
        private set
    val hasText get() = segments.isNotEmpty()
    fun speechBegan() { continuing = true; deadline = null }

    private fun tokens(value: String) = value.trim().split(Regex("\\s+"))
    private fun equalWord(a: String, b: String): Boolean =
        a.trim('.', ',', '!', '?', ':', ';').lowercase(Locale.ROOT) ==
        b.trim('.', ',', '!', '?', ':', ';').lowercase(Locale.ROOT)

    /** Only high-confidence final-to-final overlap is collapsed; never remove an unrelated word. */
    private fun fold(previous: String, next: String): String? {
        val old = tokens(previous)
        val fresh = tokens(next)
        if (old.size == fresh.size && old.indices.all { equalWord(old[it], fresh[it]) }) return previous
        // Google recognizer may first finalize "Лана" and then "Лана, сколько будет..." as two finals.
        if (old.size <= fresh.size && old.indices.all { equalWord(old[it], fresh[it]) }) return next
        // A late shorter final containing only the old prefix is not a new utterance.
        if (fresh.size < old.size && fresh.indices.all { equalWord(old[it], fresh[it]) }) return previous
        val possible = minOf(old.size, fresh.size)
        for (count in possible downTo 2) {
            if ((0 until count).all { equalWord(old[old.size - count + it], fresh[it]) }) {
                return previous + " " + fresh.drop(count).joinToString(" ")
            }
        }
        return null
    }

    fun segment(text: String, now: Long): Boolean {
        require(text.isNotBlank())
        val next = text.trim()
        val last = segments.lastOrNull()
        val folded = if(last != null) fold(last,next) else null
        val total = segments.dropLast(if(last == null) 0 else 1).sumOf { it.length } +
            if(last == null) next.length else (folded ?: last).length + if(folded == null) next.length else 0
        // Atomic rejection: never mutate the buffer if a boundary would be exceeded.
        if (total > 8000 || (folded == null && segments.size >= 16)) return false
        if (last == null) segments.add(next)
        else if (folded != null) segments[segments.lastIndex] = folded
        else segments.add(next)
        continuing = false
        deadline = now + CONTINUATION_MS
        return true
    }
    fun ready(now: Long) = hasText && !continuing && deadline?.let { now >= it } == true
    fun remaining(now: Long) = deadline?.let { (it-now).coerceAtLeast(0L) }
    fun take(): String = segments.joinToString(" ").also { clear() }
    fun clear() { segments.clear(); deadline = null; continuing = false }
}
