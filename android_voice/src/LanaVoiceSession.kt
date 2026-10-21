package ai.dreibus.lanalive

/** Main-thread-only generation guard. No microphone, network or credentials. */
internal class LanaVoiceSession {
    private var generation = 0L
    private var enabled = false
    fun start(): Long {
        generation++
        enabled = true
        return generation
    }
    fun stop() {
        generation++
        enabled = false
    }
    fun current(): Long = generation
    fun accepts(ticket: Long): Boolean = enabled && ticket == generation
}
