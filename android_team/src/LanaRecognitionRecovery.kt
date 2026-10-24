package ai.dreibus.lanalive

/** Recognition recovery only: never retries an assistant request. Android error constants are stable. */
internal class LanaRecognitionRecovery {
    data class Decision(val message: String, val delayMs: Long, val recreate: Boolean = false, val stop: Boolean = false, val diagnostic: Boolean = true)
    private var failures = 0
    private var silences = 0
    private var errorCount = 0
    private var silenceCount = 0
    private var successCount = 0
    private var lastCode: Int? = null
    fun report(): String = "Распознанных фраз: $successCount. Ожиданий без фразы: $silenceCount. Ошибок службы: $errorCount. Последний код: ${lastCode ?: "нет"}."
    fun successfulPhrase() { successCount++; reset() }
    fun reset() { failures = 0; silences = 0 }
    fun error(code: Int): Decision {
        if (code == 6 || code == 7) {
            silenceCount++
            silences++
            return Decision("Жду твою фразу", 900L, diagnostic = silences == 1)
        }
        lastCode = code
        errorCount++
        silences = 0
        failures = (failures + 1).coerceAtMost(6)
        val delay = (1000L shl (failures - 1)).coerceAtMost(30000L)
        return when (code) {
            9 -> Decision("Нет разрешения на микрофон. Разреши его в настройках приложения.", 0, stop = true)
            12, 13 -> Decision("Русское распознавание недоступно. Проверь языковой пакет Android или используй текст.", 0, stop = true)
            1, 2 -> Decision("Нет связи со службой распознавания. Повторю подключение.", delay, recreate = failures >= 2)
            3 -> Decision("Микрофон недоступен. Проверь, не занят ли он другим приложением.", delay, recreate = true)
            8 -> Decision("Распознаватель занят. Переподключаю микрофон.", delay, recreate = true)
            10 -> Decision("Слишком много запросов распознавания. Пауза 30 секунд.", 30000)
            11 -> Decision("Служба распознавания отключилась. Переподключаюсь.", delay, recreate = true)
            else -> Decision("Служба распознавания не ответила. Повторю подключение.", delay, recreate = failures >= 2)
        }
    }
    companion object {
        const val START_TIMEOUT_MS = 12000L
        const val TURN_TIMEOUT_MS = 90000L
    }
}
