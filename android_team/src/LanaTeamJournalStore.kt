package ai.dreibus.lanalive

import android.content.Context

/** One process-owned journal for Activity and Service; interrupted rows recover once after restart. */
internal object LanaTeamJournalStore {
    private var shared: LanaTeamJournal? = null
    private val screens = mutableListOf<java.lang.ref.WeakReference<MainActivity>>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    fun observe(activity: MainActivity) {
        screens.removeAll { it.get() == null || it.get()?.isDestroyed == true }
        if (screens.none { it.get() === activity }) screens.add(java.lang.ref.WeakReference(activity))
    }
    private fun changed() {
        // Post after commit/open completes; never recurse into journal recovery during construction.
        main.post {
            screens.removeAll { it.get() == null || it.get()?.isDestroyed == true }
            screens.mapNotNull { it.get() }.filter { !it.isFinishing && !it.isDestroyed }.forEach {
                runCatching { it.onTeamJournalChanged?.invoke() }
            }
        }
    }
    @Synchronized fun open(context: Context): LanaTeamJournal {
        shared?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences("lana_team_journal_v1", Context.MODE_PRIVATE)
        return LanaTeamJournal(prefs.getString("records", "[]")!!) {
            prefs.edit().putString("records", it).commit().also { saved -> if (saved) changed() }
        }.also { shared = it }
    }
}
