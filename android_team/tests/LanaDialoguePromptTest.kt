package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaDialoguePromptTest {
    @Test fun personalityAndCapabilityContextPreserveFullQuestionWithinProtocol() {
        val question = "я".repeat(1800)
        val prompt = LanaDialoguePrompt.build(question)
        assertTrue(prompt.endsWith(question))
        assertTrue(prompt.length <= 2400)
        assertTrue(prompt.contains("не соглашайся автоматически"))
        assertTrue(prompt.contains("не подключены"))
    }
}
