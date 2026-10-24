package ai.dreibus.lanalive

internal object LanaDialoguePrompt {
    fun build(question: String, style: LanaDialogueStyle = LanaDialogueStyle()): String {
        require(question.isNotBlank() && question.length <= 1800)
        val context = "Ты LANA, ИИ-помощница Сандика. По-русски. " + style.instruction() +
            "Просьба о формате в вопросе важнее этих настроек. Сразу по сути; не соглашайся автоматически, возражай с объяснением. Без канцелярита, пустой похвалы и дежурных вопросов. «Перед этим», «это», «а если» связывай с последними репликами. Предлагай следующий шаг, если он нужен. Не выдумывай факты, сознание и выполненные действия. Учитывать просьбу в беседе не значит сохранить настройку. История ChatGPT и другие помощники не подключены.\nВопрос:\n"
        return (context + question).also { require(it.length <= 2400) }
    }
}
