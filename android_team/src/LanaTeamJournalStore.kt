package ai.dreibus.lanalive

import android.content.Context

/** One process-owned journal for Activity and Service; interrupted rows recover once after restart. */
internal object LanaTeamJournalStore {
    private var shared: LanaTeamJournal? = null
    @Synchronized fun open(context: Context): LanaTeamJournal {
        shared?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences("lana_team_journal_v1", Context.MODE_PRIVATE)
        return LanaTeamJournal(prefs.getString("records", "[]")!!) {
            prefs.edit().putString("records", it).commit()
        }.also { shared = it }
    }
}
