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
    @Test fun extendedCommandsPersistEditsSearchAndRestore() {
        var saved = empty
        fun ask(q: String): String? { assertTrue(q, LanaLocalWorkspace.matches(q)); return LanaLocalWorkspace(saved) { saved=it; true }.handle(q) }
        ask("Лана, пожалуйста, добавь   задачу: Проверить голос")
        ask("Запомни: Люблю листья")
        assertTrue(ask("Сводка задач")!!.contains("Незавершённых: 1"))
        assertTrue(ask("Найди задачу голос")!!.contains("Проверить голос"))
        ask("Измени задачу 1: Проверить обновление")
        assertTrue(ask("Прочитай мои задачи")!!.contains("Проверить обновление"))
        ask("Заверши задачу один")
        assertTrue(ask("Прочитай выполненные задачи")!!.contains("Проверить обновление"))
        ask("Верни задачу один")
        assertTrue(ask("Прочитай мои задачи")!!.contains("Проверить обновление"))
        ask("Измени заметку 1: Зелёные листья")
        assertEquals("Зелёные листья", ask("Последняя заметка"))
        assertTrue(ask("Найди заметку листья")!!.contains("Зелёные листья"))
        val before=saved
        assertTrue(ask("Измени заметку 99: Нет")!!.contains("нет"))
        assertEquals(before,saved)
    }
    @Test fun editsDoNotClaimSuccessWhenStorageFails() {
        val data="{\"tasks\":[],\"notes\":[\"Исходный текст\"]}"
        assertTrue(LanaLocalWorkspace(data) { false }.handle("Измени заметку 1: Новый текст")!!.contains("Не удалось"))
        assertFalse(LanaLocalWorkspace.matches("Найди ресторан рядом"))
    }
}
