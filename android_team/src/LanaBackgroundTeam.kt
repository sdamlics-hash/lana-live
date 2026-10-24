package ai.dreibus.lanalive

/** One explicit task, durable before dispatch. No autonomous loop, retry or tool execution. */
internal class LanaBackgroundTeam(private val journal: LanaTeamJournal,
    private val transport: LanaConversationRuntime.Transport) {
    private var generation = 0L
    private var pending: String? = null
    private var operation: LanaConversationRuntime.Cancel? = null
    val running get() = pending != null
    fun submit(task: String, request: LanaRuntimeRequest, complete: (String) -> Unit) {
        check(!running) { "Другое задание ещё выполняется." }
        require(task.isNotBlank() && task.length <= LanaTeamPrompt.MAX_TASK && request.question == task)
        val id = journal.begin(task) // Failed persistence must prevent the network call.
        pending = id
        val ticket = ++generation
        fun finish(answer: String?, error: String?) {
            if (generation != ticket || pending != id) return
            pending = null; operation = null
            val valid = error == null && !answer.isNullOrBlank() && answer.length <= 5000
            val failure = error ?: "Ответ задания не получен. Автоматического повтора нет."
            val saved = runCatching {
                journal.finish(id, if (valid) answer else null, if (valid) null else failure)
            }.isSuccess
            val reply = if (valid) "Ответ API-помощника OpenAI: $answer" else failure
            complete(if (saved) reply else "Не удалось сохранить результат в журнале. $reply")
        }
        val handle = try { transport.ask(request, ::finish) } catch (_: Exception) {
            finish(null, "Не удалось запустить задание. Автоматического повтора нет.")
            return
        }
        if (generation == ticket && pending == id) operation = handle else runCatching { handle.cancel() }
    }
    fun stop() {
        generation++
        val id = pending; pending = null
        val handle = operation; operation = null
        runCatching { handle?.cancel() }
        if (id != null) journal.interrupt(id)
    }
}
