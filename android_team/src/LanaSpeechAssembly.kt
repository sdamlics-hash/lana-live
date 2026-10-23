package ai.dreibus.lanalive

/** Combines final recognition segments only. Partial hypotheses never become requests. */
internal class LanaSpeechAssembly {
    companion object { const val CONTINUATION_MS = 1400L }
    private val segments = mutableListOf<String>()
    private var deadline: Long? = null
    var continuing = false
        private set
    val hasText get() = segments.isNotEmpty()
    fun speechBegan() { continuing = true; deadline = null }
    fun segment(text: String, now: Long): Boolean {
        require(text.isNotBlank())
        // Reject the whole oversized utterance instead of silently trimming an instruction.
        if (segments.size >= 16 || segments.sumOf { it.length } + text.length > 8000) return false
        segments.add(text.trim()); continuing = false; deadline = now + CONTINUATION_MS
        return true
    }
    fun ready(now: Long) = hasText && !continuing && deadline?.let { now >= it } == true
    fun remaining(now: Long) = deadline?.let { (it-now).coerceAtLeast(0L) }
    fun take(): String = segments.joinToString(" ").also { clear() }
    fun clear() { segments.clear(); deadline = null; continuing = false }
}
