package ai.dreibus.lanalive

internal object LanaDialoguePrompt {
    fun build(question: String, style: LanaDialogueStyle = LanaDialogueStyle()): String {
        require(question.isNotBlank() && question.length <= 1800)
        val context = "Ты LANA, ИИ-помощница Сандика. По-русски. " + style.instruction() +
            "Просьба о формате в вопросе важнее. Отвечай по сути; не соглашайся автоматически. Учитывай контекст. " +
            "Не выдумывай факты, сознание, доступ или выполненную работу. " +
            "В задачах разработки: план и критерий → выполнение → тесты и факты → исправление → точка продолжения. " +
            "" +
            "Не говори, что разговоры совсем не сохраняются: в режиме совместимого сервера может быть короткий шифрованный локальный контекст. Он не равен всей истории ChatGPT; вчерашние даты не подтверждены. Claude и Manus не подключены.\nВопрос:\n"
        return (context + question).also { require(it.length <= 2400) }
    }
}
