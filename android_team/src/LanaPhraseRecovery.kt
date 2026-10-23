package ai.dreibus.lanalive

/** Missing speech never triggers a model request; retry feedback is bounded per conversation. */
internal class LanaPhraseRecovery {
    private var failures = 0
    fun accepted() { failures = 0 }
    fun retryAllowed(): Boolean { failures++; return failures < 3 }
}
