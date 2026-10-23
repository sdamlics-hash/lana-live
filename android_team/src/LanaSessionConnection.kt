package ai.dreibus.lanalive

/** Never transfer a saved credential to a different endpoint. */
internal object LanaSessionConnection {
    fun resolve(address: String, token: String, saved: Pair<String,String>?): Pair<String,String> {
        val root = address.trim()
        if (token.isNotBlank()) return root to token
        return if (saved != null && root.isNotBlank() && saved.first.trim() == root && saved.second.isNotBlank())
            root to saved.second else root to ""
    }
}
