package ai.dreibus.lanalive

/** Local replies: no model request, no invented identity or connection status. */
internal object LanaGreetings {
    private val replies = listOf(
        "Да, я здесь. Рассказывай.",
        "Я тут. Что у тебя?",
        "Слушаю. Давай разберёмся.",
        "Да, на связи. О чём поговорим?"
    )
    private var next = 0
    @Synchronized fun next(): String {
        val reply = replies[next]
        next = (next + 1) % replies.size
        return reply
    }
}
