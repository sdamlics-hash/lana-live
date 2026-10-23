package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaLocalWorkspaceTest {
    @Test fun observedReadVariantsUseLocalTasksRatherThanModel() {
        for (q in listOf("Почитай мои задачи", "Зачитай мои задачи", "Лана, почитай мои задачи")) {
            assertTrue(q, LanaLocalWorkspace.matches(q))
            assertEquals("Незавершённых задач нет.", LanaLocalWorkspace("{\"tasks\":[],\"notes\":[]}") { true }.handle(q))
        }
    }

    private val empty = "{\"tasks\":[],\"notes\":[]}"
    @Test fun notesAndTasksPersistTogetherAndDuplicateIsBlocked() {
        var saved = empty
        fun store() = LanaLocalWorkspace(saved) { saved = it; true }
        assertTrue(store().handle("Запомни: Я люблю зелёный цвет")!!.contains("Запомнила"))
        store().handle("Добавь задачу: Проверить Лану")
        assertTrue(store().handle("Прочитай мои задачи")!!.contains("Проверить Лану"))
        assertTrue(store().handle("Что ты запомнила?")!!.contains("зелёный"))
        assertTrue(store().handle("добавь задачу: Проверить Лану")!!.contains("Дубликат"))
        store().handle("Заверши задачу номер 1")
        assertEquals("Незавершённых задач нет.", store().handle("Какие у меня задачи?"))
    }
    @Test fun failureDoesNotClaimSuccessOrChangeStoredBytes() {
        var saved = empty
        val store = LanaLocalWorkspace(saved) { false }
        assertTrue(store.handle("Запомни: тест")!!.contains("Не удалось"))
        assertEquals(empty, saved)
    }
    @Test fun normalQuestionsAreNotInterceptedAndInvalidNumbersDoNotMutate() {
        assertFalse(LanaLocalWorkspace.matches("Что такое память?"))
        assertNull(LanaLocalWorkspace(empty) { fail("Must not write"); false }.handle("Какая погода"))
        assertTrue(LanaLocalWorkspace(empty) { fail("Must not write"); false }.handle("Заверши задачу 9")!!.contains("нет"))
    }
}
