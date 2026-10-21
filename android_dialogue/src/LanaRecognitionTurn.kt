package ai.dreibus.lanalive

/** Main-thread single recognition turn. Never sends partial text or starts a model call. */
internal class LanaRecognitionTurn {
    companion object {
        const val TARGET_SILENCE_MS = 1300L
        const val INCOMPLETE_SILENCE_MS = 1500L
        const val FINAL_TIMEOUT_MS = 6500L
    }
    private var finished = false
    private var finalDeadline: Long? = null
    var endedAt: Long? = null
        private set

    fun endOfSpeech(now: Long): Boolean {
        if (finished || finalDeadline != null) return false
        endedAt = now
        finalDeadline = now + FINAL_TIMEOUT_MS
        return true
    }
    fun speechBegan() {
        if (!finished) { finalDeadline = null; endedAt = null }
    }
    fun acceptsEvents(): Boolean = !finished
    fun finish(): Boolean {
        if (finished) return false
        finished = true
        finalDeadline = null
        return true
    }
    fun timeout(now: Long): Boolean {
        val deadline = finalDeadline ?: return false
        return now >= deadline && finish()
    }
}
