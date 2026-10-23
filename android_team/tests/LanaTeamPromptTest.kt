package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaTeamPromptTest {
    @Test fun completeTaskAndProjectFitTransportLimit() {
        val task = "я".repeat(LanaTeamPrompt.MAX_TASK)
        val prompt = LanaTeamPrompt.build(task)
        assertTrue(prompt.length <= 2400)
        assertTrue(prompt.endsWith(task))
        assertTrue(prompt.contains("Android-приложение"))
        assertTrue(prompt.contains("Claude и Manus не подключены"))
        assertTrue(prompt.contains("конкретная проверка"))
    }
    @Test fun oversizedTaskIsRejectedRatherThanSilentlyTruncated() {
        for (task in listOf(" ", "я".repeat(LanaTeamPrompt.MAX_TASK + 1))) {
            try { LanaTeamPrompt.build(task); fail("Must reject invalid task") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
