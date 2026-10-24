package ai.dreibus.lanalive

import java.util.Locale

/** Reject only an isolated leading arithmetic operator with an operand.
 * This is a strong signal that SpeechRecognizer lost the first operand.
 * The user may retry; never send the fragment to the model as a complete question.
 */
internal object LanaIncompleteSpeech {
    private val number = "(?:[0-9]+|ноль|один|одна|одно|два|две|три|четыре|пять|шесть|семь|восемь|девять|десять|одиннадцать|двенадцать|тринадцать|четырнадцать|пятнадцать|шестнадцать|семнадцать|восемнадцать|девятнадцать|двадцать|тридцать|сорок|пятьдесят|шестьдесят|семьдесят|восемьдесят|девяносто|сто|двести|триста|четыреста|пятьсот|тысяча)"
    private val dangling = Regex("^(?:плюс|минус|умножить на|помножить на|делить на|разделить на)\\s+$number(?:\\s+$number){0,3}$")
    fun missingFirstOperand(utterance: String): Boolean {
        val q = utterance.lowercase(Locale.ROOT).replace('ё', 'е').trim()
            .replace(Regex("^[.,!?;:]+|[.,!?;:]+$"), "")
            .replace(Regex("^(?:лана|ладно)\\s*[,.:]?\\s+"), "")
            .replace(Regex("\\s+"), " ").trim()
        return dangling.matches(q)
    }
}
