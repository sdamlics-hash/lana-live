package ai.dreibus.lanalive

internal class LanaSessionTrace {
    private val rows = java.util.ArrayDeque<String>()
    fun add(event: String) {
        require(event.length <= 180 && !event.contains('\n'))
        if (rows.size == 24) rows.removeFirst()
        rows.addLast(event)
    }
    fun report() = rows.joinToString("\n")
}
