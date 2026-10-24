package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaDialogueStyleTest {
    private fun choices(): List<LanaDialogueStyle> = (0..7).map { LanaDialogueStyle(it and 1 != 0,it and 2 != 0,it and 4 != 0) }
    @Test fun allExplicitChoicesSurviveStoreRecreationAndKeepFullMaximumQuestion() {
        var disk: String? = null
        fun store() = LanaDialogueStyleStore({ disk },{ disk = it; true })
        assertEquals(LanaDialogueStyle(),store().load().style)
        for (style in choices()) {
            assertTrue(store().save(style))
            val restored = store().load()
            assertTrue(restored.readable)
            assertEquals(style,restored.style)
            val question = "я".repeat(1800)
            val prompt = LanaDialoguePrompt.build(question,restored.style)
            assertTrue(prompt.endsWith(question))
            assertTrue(prompt.length <= 2400)
            assertTrue(prompt.contains(style.instruction()))
            assertTrue(prompt.contains("не соглашайся автоматически"))
            assertTrue(prompt.contains("не подключены"))
        }
    }
    @Test fun readingInvalidOrUnavailableSettingsNeverWritesOrClaimsSuccessfulRestore() {
        for (raw in listOf("", "2|1|1|1", "1|yes|1|1", "1|1|1", "1|1|1|1|extra")) {
            var writes = 0
            val loaded = LanaDialogueStyleStore({ raw },{ writes++; true }).load()
            assertFalse(loaded.readable)
            assertEquals(LanaDialogueStyle(),loaded.style)
            assertEquals(0,writes)
        }
        assertFalse(LanaDialogueStyleStore({ throw IllegalStateException("private storage detail") },{ true }).load().readable)
    }
    @Test fun failedSaveDoesNotReportSuccessAndExplicitSaveCanReplaceInvalidSettings() {
        assertFalse(LanaDialogueStyleStore({ null },{ false }).save(LanaDialogueStyle()))
        assertFalse(LanaDialogueStyleStore({ null },{ throw IllegalStateException("disk failed") }).save(LanaDialogueStyle()))
        var disk = "corrupt"
        val store = LanaDialogueStyleStore({ disk },{ disk = it; true })
        assertFalse(store.load().readable)
        val selected = LanaDialogueStyle(false,false,false)
        assertTrue(store.save(selected))
        assertEquals(selected,store.load().style)
        assertTrue(store.load().readable)
    }
    @Test fun everyModeKeepsQuestionBoundsAndDoesNotInsertAnswers() {
        for (style in choices()) {
            for (invalid in listOf("", "я".repeat(1801))) {
                try { LanaDialoguePrompt.build(invalid,style); fail() } catch (_: IllegalArgumentException) {}
            }
            assertTrue(LanaDialoguePrompt.build("Объясни подробнее",style).contains("Просьба о формате в вопросе важнее"))
        }
    }
}
