package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaDialoguePromptTest {
    @Test fun observedImprovementQuestionsPreserveUserRequestAndCapabilityBoundary() {
        for (question in listOf("Как ты видишь своё будущее?", "Какие улучшения ты бы желала?", "Я хочу, чтобы ты стала лучше", "Мне нужен твой конкретный план")) {
            val prompt = LanaDialoguePrompt.build(question)
            assertTrue(prompt.endsWith(question))
            assertTrue(prompt.length <= 2400)
        }
        // Bounds are the transport contract, not evidence of model response quality.
        try { LanaDialoguePrompt.build("я".repeat(1801)); fail("Reject before transport") } catch (_: IllegalArgumentException) {}
    }
    @Test fun personalityAndCapabilityContextPreserveFullQuestionWithinProtocol() {
        val question = "я".repeat(1800)
        val prompt = LanaDialoguePrompt.build(question)
        assertTrue(prompt.endsWith(question))
        assertTrue(prompt.length <= 2400)
        assertTrue(prompt.contains("не соглашайся автоматически"))
        assertTrue(prompt.contains("не подключены"))
    }
}
