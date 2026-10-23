package ai.dreibus.lanalive

/** Single session epoch for delayed listening and exactly-once final recognition dispatch. */
internal class LanaHandsFreeGate {
    private var epoch = 0L
    private var pending: Long? = null
    var active = false
        private set
    fun start() { stop(); active = true }
    fun stop() { active = false; epoch++; pending = null }
    fun schedule(): Long? = if (active) (++epoch).also { pending = null } else null
    fun begin(token: Long): Boolean {
        if (!active || token != epoch || pending != null) return false
        pending = token
        return true
    }
    fun accept(token: Long): Boolean {
        if (!active || pending != token) return false
        pending = null
        return true
    }
}
