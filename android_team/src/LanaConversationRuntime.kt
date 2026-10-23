package ai.dreibus.lanalive

/** Main-thread conversation state machine; has no Activity, View, Android API or automatic request retry. */
internal class LanaRuntimeRequest(
    val address: String, val token: String, val conversationId: String,
    val question: String, val prompt: String, val legacy: Boolean,
    notes: List<String>
) {
    val notes = notes.toList()
    override fun toString() = "LanaRuntimeRequest(redacted)"
}
internal class LanaRuntimeVoice(
    val enabled: Boolean, val cloud: Boolean, val address: String, val token: String,
    val voiceName: String, val rate: Float, val historyPreferences: String = "LanaRuntimeHistory"
) { override fun toString() = "LanaRuntimeVoice(redacted)" }
internal class LanaConversationRuntime(
    private val transport: Transport,
    private val speaker: Speaker,
    private val accepted: (LanaRuntimeRequest, String) -> Boolean = { _, _ -> true }
) {
    fun interface Cancel { fun cancel() }
    interface Transport { fun ask(request: LanaRuntimeRequest, complete: (String?, String?) -> Unit): Cancel }
    interface Speaker { fun speak(text: String, voice: LanaRuntimeVoice, started: () -> Unit, complete: (String?) -> Unit): Cancel }
    enum class Phase { IDLE, THINKING, PREPARING_VOICE, SPEAKING, READY, ERROR, STOPPED }
    data class Snapshot(val turn: Long, val clientTicket: Long, val phase: Phase, val answer: String = "", val error: String? = null, val textOnly: Boolean = false, val contextSaved: Boolean = true)
    private var generation = 0L
    private var operation: Cancel? = null
    private var observer: ((Snapshot) -> Unit)? = null
    var snapshot = Snapshot(0,-1,Phase.IDLE)
        private set
    fun observe(listener: ((Snapshot) -> Unit)?) { observer = listener; listener?.invoke(snapshot) }
    private fun publish(value: Snapshot) { snapshot = value; observer?.invoke(value) }
    private fun begin(ticket: Long, phase: Phase, textOnly: Boolean): Long {
        generation++
        val old = operation; operation = null
        // Invalidate before cancellation, including cancellation implementations with synchronous callbacks.
        runCatching { old?.cancel() }
        val id = generation
        publish(Snapshot(id,ticket,phase,textOnly=textOnly))
        return id
    }
    fun ask(request: LanaRuntimeRequest, voice: LanaRuntimeVoice, clientTicket: Long) {
        val id = begin(clientTicket,Phase.THINKING,!voice.enabled)
        if (generation != id) return
        var returned = false
        val cancel = try { transport.ask(request) { answer, error ->
            if (generation != id || snapshot.phase != Phase.THINKING) return@ask
            returned = true; operation = null
            if (error != null || answer.isNullOrBlank()) {
                publish(snapshot.copy(phase=Phase.ERROR,error=error ?: "Сервер вернул пустой ответ"))
            } else {
                val saved = try { accepted(request,answer) } catch (_: Exception) { false }
                if (generation == id) output(id,answer,voice,saved)
            }
        } } catch (_: Exception) {
            if (generation == id) publish(snapshot.copy(phase=Phase.ERROR,error="Не удалось запустить запрос. Автоматического повтора нет."))
            return
        }
        if (!returned && generation == id) operation = cancel else runCatching { cancel.cancel() }
    }
    fun say(text: String, voice: LanaRuntimeVoice, clientTicket: Long) {
        require(text.isNotBlank())
        val id = begin(clientTicket,Phase.PREPARING_VOICE,!voice.enabled)
        if (generation == id) output(id,text,voice,true)
    }
    private fun output(id: Long, answer: String, voice: LanaRuntimeVoice, saved: Boolean) {
        if (generation != id) return
        if (!voice.enabled) { publish(snapshot.copy(phase=Phase.READY,answer=answer,contextSaved=saved)); return }
        publish(snapshot.copy(phase=Phase.PREPARING_VOICE,answer=answer,contextSaved=saved))
        if (generation != id) return
        var returned = false
        val cancel = try { speaker.speak(answer,voice,{
            if (generation == id && snapshot.phase == Phase.PREPARING_VOICE) publish(snapshot.copy(phase=Phase.SPEAKING))
        }) { error ->
            if (generation != id || snapshot.phase !in setOf(Phase.PREPARING_VOICE,Phase.SPEAKING)) return@speak
            returned = true; operation = null
            publish(snapshot.copy(phase=if (error == null) Phase.READY else Phase.ERROR,error=error))
        } } catch (_: Exception) {
            if (generation == id) publish(snapshot.copy(phase=Phase.ERROR,error="Не удалось запустить озвучку. Ответ доступен текстом."))
            return
        }
        if (!returned && generation == id) operation = cancel else runCatching { cancel.cancel() }
    }
    fun stop() {
        generation++
        val old = operation; operation = null
        runCatching { old?.cancel() }
        publish(snapshot.copy(phase=Phase.STOPPED))
    }
}
