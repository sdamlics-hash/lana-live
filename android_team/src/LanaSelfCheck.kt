package ai.dreibus.lanalive

import java.util.Date
import java.util.TimeZone
import javax.crypto.KeyGenerator

/** Uses synthetic records and a disposable key; never touches the live connection or journal. */
internal object LanaSelfCheck {
    fun run(roundTrip: (String) -> String): String {
        val results = mutableListOf<String>()
        fun checkPart(name: String, action: () -> Unit) {
            try { action(); results.add("OK · $name") }
            catch (_: Exception) { results.add("ОШИБКА · $name") }
        }
        checkPart("Запись и чтение тестового файла") { check(roundTrip("LANA-check-ёж") == "LANA-check-ёж") }
        checkPart("Часы: отдельный и объединённый вопрос") {
            val now = Date(0)
            val zone = TimeZone.getTimeZone("UTC")
            check(LanaDeviceClock.answer("Сколько сейчас времени", now, zone)?.contains("00:00") == true)
            check(LanaDeviceClock.answer("Какая сегодня дата и сколько времени", now, zone) != null)
            check(LanaDeviceClock.answer("Какая погода", now, zone) == null)
        }
        checkPart("Контекст команды и полный текст задания") {
            val task = "я".repeat(LanaTeamPrompt.MAX_TASK)
            val prompt = LanaTeamPrompt.build(task)
            check(prompt.length <= 2400 && prompt.endsWith(task))
        }
        checkPart("Журнал: ответ, отклонение, возврат, повторное чтение") {
            var stored = "[]"
            val journal = LanaTeamJournal(stored) { stored = roundTrip(it); true }
            val id = journal.begin("Тестовая задача")
            journal.finish(id, "Тестовый ответ", null)
            journal.reject(id, "Тест проверки")
            journal.reopen(id)
            val restored = LanaTeamJournal(stored) { true }
            val row = restored.filtered("тестовая", "review").getJSONObject(0)
            check(row.getString("answer") == "Тестовый ответ")
            check(row.getJSONArray("reviewHistory").length() == 1)
        }
        checkPart("Шифрование и обнаружение повреждения на тестовом ключе") {
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            val plain = "Только тестовые данные".toByteArray()
            val encrypted = LanaConnectionCipher.encrypt(plain, key)
            check(LanaConnectionCipher.decrypt(encrypted, key).contentEquals(plain))
            val damaged = encrypted.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
            var rejected = false
            try { LanaConnectionCipher.decrypt(damaged, key) } catch (_: java.security.GeneralSecurityException) { rejected = true }
            check(rejected)
        }
        return results.joinToString("\n") + "\n\nЭто локальные проверки компонентов. Сервер, микрофон, озвучка, AndroidKeyStore и работа с выключенным экраном здесь не проверялись."
    }
}
