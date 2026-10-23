package ai.dreibus.lanalive

/** Monotonic client timings only. No inference about provider computation or model quality. */
internal data class LanaDialogueTiming(
    val queuedAt: Long, val startedAt: Long, val finishedAt: Long,
    val historyPairs: Int, val success: Boolean
) {
    init {
        require(queuedAt >= 0 && startedAt >= queuedAt && finishedAt >= startedAt)
        require(historyPairs in 0..8)
    }
    fun report(): String = "dialogue queue_ms=${startedAt-queuedAt} request_ms=${finishedAt-startedAt} " +
        "local_pairs=$historyPairs result=${if (success) "ok" else "error"}"
}
