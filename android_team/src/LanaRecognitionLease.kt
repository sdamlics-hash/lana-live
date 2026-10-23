package ai.dreibus.lanalive

/** Main-thread ownership and generation guard, independent from an Activity lifecycle. */
internal class LanaRecognitionLease {
    private var owner: String? = null
    private var generation = 0L
    private var active = false
    fun owns(client: String): Boolean = owner == client
    fun attach(client: String) { require(client.isNotBlank()); generation++; owner = client; active = false }
    fun begin(client: String): Long? {
        if (!owns(client)) return null
        generation++; active = true
        return generation
    }
    fun accepts(client: String, ticket: Long) = owns(client) && active && generation == ticket
    fun finish(client: String, ticket: Long): Boolean {
        if (!accepts(client, ticket)) return false
        active = false
        return true
    }
    fun cancel(client: String): Boolean {
        if (!owns(client)) return false
        generation++; active = false
        return true
    }
    fun detach(client: String): Boolean {
        if (!cancel(client)) return false
        owner = null
        return true
    }
}
