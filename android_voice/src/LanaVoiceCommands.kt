package ai.dreibus.lanalive

import java.util.Locale

/**
 * Pure speech-command classifier. It neither records microphone audio,
 * performs network operations, nor executes any sensitive actions.
 *
 * Stop commands are checked FIRST, including those containing "Лана".
 * Follow-up questions require an explicitly active conversation.
 */
internal object LanaVoiceCommands {
    enum class Action { IGNORE, WAKE, ASK, STOP }

    fun choose(recognized: String, activeConversation: Boolean): Action {
        val normalized = recognized.trim()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(Regex("[.,!?;:]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isEmpty()) return Action.IGNORE

        // Stopping has priority over any wake word, even "Лана, стоп".
        val stopPhrases = listOf(
            "стоп разговор", "останови разговор",
            "лана стоп", "лана хватит", "лана замолчи", "лана все", "лана остановись",
            "хватит лана", "все лана", "пока лана"
        )
        if (stopPhrases.any { normalized == it ||
                normalized.startsWith("$it ") || normalized.endsWith(" $it") }) {
            return if (activeConversation) Action.STOP else Action.IGNORE
        }

        val isWake = normalized == "лана" ||
            normalized == "лана ты тут" ||
            normalized == "ладно ты тут" ||
            normalized == "ладно ты здесь" ||
            normalized == "лана ты здесь" ||
            normalized == "лана слушаешь" ||
            normalized == "лана привет"
        if (isWake) return Action.WAKE
        if (normalized.contains(Regex("(^|\\s)лана(\\s|$)"))) return Action.ASK
        if (activeConversation) return Action.ASK
        return Action.IGNORE
    }
}
