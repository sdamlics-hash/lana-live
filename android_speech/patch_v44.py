"""Native Shimmer playback, retaining V4.3 local TTS as an explicit setting."""
import importlib.util
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
A='{http://schemas.android.com/apk/res/android}'
def one(s,old,new):
    if s.count(old)!=1:raise ValueError('Voice anchor changed: '+old[:60])
    return s.replace(old,new,1)
def make_preview(archive,destination):
    spec=importlib.util.spec_from_file_location('v43',ROOT/'android_voice/repair_v43.py')
    base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
    out=base.make_preview(archive,destination)
    src=out/'app/src/main/java/ai/dreibus/lanalive';p=src/'MainActivity.kt';s=p.read_text()
    # The token field is cleared after use; another status check must reuse
    # the in-memory session token rather than silently erase it.
    s=one(s,'    private var voiceSessionToken: String? = null',
        '    private var voiceSessionToken: String? = null\n    private var voiceSessionTokenAddress: String? = null')
    s=one(s,'val secret = secretField.text.toString()',
        'val secret = secretField.text.toString().trim().ifBlank {\n                if (voiceSessionTokenAddress == address.trim()) voiceSessionToken.orEmpty() else ""\n            }')
    s=one(s,'            voiceSessionToken = secret',
        '            voiceSessionToken = secret\n            voiceSessionTokenAddress = address.trim()')
    s=one(s,'if (secret.isNullOrBlank() || address.isBlank()) {',
        'if (secret.isNullOrBlank() || address.isBlank() || voiceSessionTokenAddress != address) {')
    s=one(s,'    private var ttsReady = false','''    private var ttsReady = false
    private var cloudPlayer: android.media.MediaPlayer? = null
    private var cloudClip: java.io.File? = null
    private var cloudAudioGeneration = 0L''')
    s=one(s,'    private fun speakFromLana(answer: String) {','''    private fun speakFromLana(answer: String) {
        if (getPreferences(MODE_PRIVATE).getBoolean("cloud_voice", true)) {
            speakCloudVoice(answer)
            return
        }''')
    # Cloud preview must not depend on Android's optional Russian voice data.
    s=one(s,'            if (!ttsReady) { voiceDiagnostic("Русский синтез речи ещё не готов"); return@setOnClickListener }','''            if (!getPreferences(MODE_PRIVATE).getBoolean("cloud_voice", true) && !ttsReady) {
                voiceDiagnostic("Русский синтез речи ещё не готов"); return@setOnClickListener
            }''')
    s=one(s,'    private fun installVoiceControls() {','''    private fun installVoiceControls() {
        findViewById<android.widget.CheckBox>(R.id.cloudVoice).apply {
            isChecked = getPreferences(MODE_PRIVATE).getBoolean("cloud_voice", true)
            setOnCheckedChangeListener { _, value ->
                stopCloudVoice()
                getPreferences(MODE_PRIVATE).edit().putBoolean("cloud_voice", value).apply()
                voiceDiagnostic(if (value) "Выбран Shimmer · 1,25×" else "Выбран системный голос Android")
            }
        }''')
    s=s.replace('                tts?.stop()','                stopCloudVoice()\n                tts?.stop()')
    s=one(s,'        tts?.shutdown()','        stopCloudVoice()\n        tts?.shutdown()')
    methods=r'''
    private fun stopCloudVoice() {
        cloudAudioGeneration++
        cloudPlayer?.release()
        cloudPlayer = null
        cloudClip?.delete()
        cloudClip = null
        speaking = false
    }

    private fun speakCloudVoice(answer: String) {
        stopCloudVoice()
        appendHistory("Лана: " + answer)
        val address = findViewById<EditText>(R.id.coreServerUrl).text.toString().trim()
        val token = voiceSessionToken.orEmpty()
        if (address.isBlank() || token.isBlank() || voiceSessionTokenAddress != address) {
            voiceDiagnostic("Для Shimmer нужны адрес ядра и ключ текущего сеанса")
            status.text = "Ответ есть; голос не подключён"
            retryVoice()
            return
        }
        speaking = true
        recognizer?.cancel()
        tts?.stop()
        val ticket = voiceSession.current()
        val audioGeneration = cloudAudioGeneration
        status.text = "Готовлю голос Shimmer…"
        voiceDiagnostic("Запрашиваю Shimmer · 1,25×")
        Thread {
            var result: ByteArray? = null
            var error: String? = null
            try { result = LanaSpeechClient.audio(address, token, answer) }
            catch (e: Exception) {
                error = when (e) {
                    is java.io.IOException, is IllegalArgumentException -> e.message
                    else -> "Не удалось подготовить голос"
                }
            }
            val bytes = result
            val problem = error
            runOnUiThread {
                if (!voiceEnabled || isDestroyed || isFinishing ||
                    !voiceSession.accepts(ticket) || cloudAudioGeneration != audioGeneration) return@runOnUiThread
                if (bytes == null) {
                    speaking = false
                    status.text = "Ответ сохранён; озвучка не получена"
                    voiceDiagnostic(problem ?: "Нет связи с голосом")
                    retryVoice()
                    return@runOnUiThread
                }
                try {
                    val file = java.io.File.createTempFile("lana-speech-", ".mp3", cacheDir)
                    cloudClip = file
                    file.writeBytes(bytes)
                    val player = android.media.MediaPlayer()
                    cloudPlayer = player
                    player.setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    player.setOnPreparedListener {
                        if (cloudPlayer !== player || !voiceEnabled || !voiceSession.accepts(ticket)) return@setOnPreparedListener
                        status.text = "Отвечаю"
                        voiceDiagnostic("Звучит Shimmer · 1,25×")
                        player.start()
                    }
                    player.setOnCompletionListener {
                        if (cloudPlayer !== player || !voiceSession.accepts(ticket)) return@setOnCompletionListener
                        stopCloudVoice()
                        voiceDiagnostic("Shimmer завершил ответ")
                        handler.postDelayed({
                            if (voiceEnabled && !speaking && voiceSession.accepts(ticket)) listenOnce()
                        }, 300)
                    }
                    player.setOnErrorListener { _, _, _ ->
                        if (cloudPlayer === player) {
                            stopCloudVoice()
                            status.text = "Не удалось воспроизвести ответ"
                            voiceDiagnostic("Ошибка воспроизведения Shimmer")
                            retryVoice()
                        }
                        true
                    }
                    player.setDataSource(file.absolutePath)
                    player.prepareAsync()
                } catch (_: Exception) {
                    stopCloudVoice()
                    status.text = "Не удалось воспроизвести ответ"
                    voiceDiagnostic("Ошибка подготовки записи Shimmer")
                    retryVoice()
                }
            }
        }.start()
    }

'''
    s=one(s,'    private fun appendHistory(line: String) {',methods+'    private fun appendHistory(line: String) {');p.write_text(s)
    shutil.copyfile(ROOT/'android_speech/src/LanaSpeechClient.kt',src/'LanaSpeechClient.kt')
    layout=out/'app/src/main/res/layout/activity_main.xml';tree=ET.fromstring(layout.read_text())
    settings=next(n for n in tree.iter() if n.get(A+'id')=='@+id/workspaceSettings')
    settings.insert(0,ET.Element('CheckBox',{A+'id':'@+id/cloudVoice',A+'layout_width':'match_parent',A+'layout_height':'wrap_content',A+'text':'Голос Shimmer · 1,25× (через сервер)',A+'contentDescription':'cloud_voice',A+'textColor':'#342C2A',A+'saveEnabled':'false'}))
    settings.insert(1,ET.Element('TextView',{A+'layout_width':'match_parent',A+'layout_height':'wrap_content',A+'text':'Голос создан ИИ. При выключенном Shimmer используются голос и скорость Android, выбранные ниже.',A+'textColor':'#796258'}))
    layout.write_text(ET.tostring(tree,encoding='unicode').replace('LANA Live V4.3','LANA Live V4.4'))
    # Keep the recognition service bound between completed turns. Destroying
    # and immediately rebinding it on every answer races the service teardown.
    s=p.read_text()
    s=one(s, '    private var speaking = false', '''    private var speaking = false
    private var activeRecognitionTurn: LanaRecognitionTurn? = null

    private fun cancelRecognition() {
        activeRecognitionTurn?.finish()
        activeRecognitionTurn = null
        recognizer?.cancel()
    }''')
    s=one(s, '''        val turn = LanaRecognitionTurn()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {''', '''        if (activeRecognitionTurn?.acceptsEvents() == true) return
        val turn = LanaRecognitionTurn()
        activeRecognitionTurn = turn
        status.text = "Подключаю микрофон…"
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.apply {''')
    # Closing the turn before intentional cancel prevents late callbacks from
    # scheduling a retry during synthesis or accepting the assistant's voice.
    s=s.replace('recognizer?.cancel()', 'cancelRecognition()')
    s=one(s, '''        activeRecognitionTurn = null
        cancelRecognition()''', '''        activeRecognitionTurn = null
        recognizer?.cancel()''')
    s=one(s, '''                    } else { voiceDiagnostic("Ошибка распознавания: " + error); retryVoice() }''', '''                    } else {
                        if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) {
                            recognizer?.destroy()
                            recognizer = null
                            voiceDiagnostic("Восстанавливаю соединение с распознавателем")
                        } else voiceDiagnostic("Ошибка распознавания: " + error)
                        retryVoice()
                    }''')
    # Check for an already running turn before advancing the session ticket;
    # otherwise a duplicate scheduled retry would invalidate its callbacks.
    s=one(s, '''        val recognitionTicket = voiceSession.start()
        if (activeRecognitionTurn?.acceptsEvents() == true) return''', '''        if (activeRecognitionTurn?.acceptsEvents() == true) return
        val recognitionTicket = voiceSession.start()''')
    s=one(s, '    private fun askFromLana(question: String) {', '''    private fun askFromLana(question: String) {
        val clockAnswer = LanaDeviceClock.answer(question)
        if (clockAnswer != null) {
            chatRequestStartedAt = 0L
            voiceDiagnostic("Дата и время: часы планшета")
            speakFromLana(clockAnswer)
            return
        }''')
    (src/'LanaDeviceClock.kt').write_text(r'''package ai.dreibus.lanalive

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Narrow local intents only. Never guess current facts from a language model. */
internal object LanaDeviceClock {
    private val dateQuestion = Regex(
        "(?:какое (?:сегодня )?число|какая (?:сегодня )?дата|какой сегодня день" +
        "|какой (?:сейчас |сегодня )?(?:год|месяц|день недели)" +
        "|(?:назови|скажи) (?:сегодняшнюю |текущую )?дату)")
    private val timeQuestion = Regex(
        "(?:сколько (?:сейчас )?(?:времени|время)|который (?:сейчас )?час" +
        "|какое (?:сейчас |текущее )?время|(?:назови|скажи) (?:текущее )?время)")

    fun answer(question: String, now: Date = Date(), zone: TimeZone = TimeZone.getDefault()): String? {
        val text = question.lowercase(Locale.ROOT).replace('ё', 'е')
            .replace(Regex("[.,!?;:]"), " ").replace(Regex("\\s+"), " ").trim()
            .removePrefix("лана ").removePrefix("ладно ").removePrefix("пожалуйста ").removeSuffix(" пожалуйста").trim()
        val date = dateQuestion.matches(text) ||
            text == "какой сегодня день и какой год какой месяц" ||
            text == "какой сегодня день и какой год и какой месяц"
        val time = timeQuestion.matches(text)
        // Full matching avoids intercepting historical dates, time zones,
        // weather, reminders, or a question containing additional requests.
        if (!date && !time) return null
        val pattern = if (date) "EEEE, d MMMM yyyy 'года'" else "HH:mm"
        val formatted = SimpleDateFormat(pattern, Locale("ru", "RU")).apply { timeZone = zone }.format(now)
        return if (date) "По часам планшета сегодня $formatted."
            else "По часам планшета сейчас $formatted."
    }
}
''')
    tests=out/'app/src/test/java/ai/dreibus/lanalive'
    (tests/'LanaDeviceClockTest.kt').write_text(r'''package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Date
import java.util.TimeZone

class LanaDeviceClockTest {
    private fun at(value: String) = Date.from(Instant.parse(value))
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    @Test fun spokenCompoundQuestionUsesActualCalendarDate() {
        val reply = LanaDeviceClock.answer("Какой сегодня день и какой год какой месяц?",
            at("2026-10-23T14:13:00Z"), berlin)!!
        assertTrue(reply.contains("пятница, 23 октября 2026 года"))
    }
    @Test fun currentTimeUsesDeviceZoneAndChangesAcrossDayBoundary() {
        assertEquals("По часам планшета сейчас 00:05.",
            LanaDeviceClock.answer("Лана, сколько сейчас время?", at("2026-10-23T22:05:00Z"), berlin))
        assertTrue(LanaDeviceClock.answer("Какая сегодня дата", at("2026-10-23T22:05:00Z"), berlin)!!.contains("24 октября"))
    }
    @Test fun daylightSavingIsCalculatedForTheRequestedInstant() {
        assertTrue(LanaDeviceClock.answer("Который час", at("2026-10-25T00:30:00Z"), berlin)!!.contains("02:30"))
        assertTrue(LanaDeviceClock.answer("Который час", at("2026-10-25T01:30:00Z"), berlin)!!.contains("02:30"))
        assertTrue(LanaDeviceClock.answer("Который час", at("2026-10-25T02:30:00Z"), berlin)!!.contains("03:30"))
    }
    @Test fun unrelatedOrCompoundRequestsContinueToServer() {
        for (question in listOf("В каком году основана Рига", "Какой сегодня день рождения у Петра",
            "Сколько времени нужно на дорогу", "Какая погода на улице", "Какое время в Токио",
            "Скажи дату и погоду", "Какая сегодня дата и поставь будильник", "")) {
            assertNull(question, LanaDeviceClock.answer(question, at("2026-10-23T14:00:00Z"), berlin))
        }
    }
    @Test fun shortDateAndYearRequestsAreRecognized() {
        for (question in listOf("Какое сегодня число?", "Какой сейчас год", "Какой сейчас месяц",
            "Лана, скажи дату, пожалуйста")) {
            assertTrue(question, LanaDeviceClock.answer(question, at("2026-10-23T14:00:00Z"), berlin)!!.contains("2026"))
        }
    }
}
''')
    s=one(s, '        installVoiceControls()', '''        installVoiceControls()
        restoreConnection()''')
    s=one(s, '            voiceSessionToken = secret', '''            try {
                LanaConnectionVault(this).save(address.trim(), secret)
                voiceDiagnostic("Адрес и ключ сохранены на устройстве с шифрованием")
            } catch (_: Exception) {
                voiceDiagnostic("Не удалось сохранить подключение; оно доступно только до закрытия приложения")
            }
            voiceSessionToken = secret''')
    restore=r'''
    private fun restoreConnection() {
        val settings = findViewById<android.widget.LinearLayout>(R.id.workspaceSettings)
        settings.addView(Button(this).apply {
            text = "ЗАБЫТЬ СОХРАНЁННОЕ ПОДКЛЮЧЕНИЕ"
            setOnClickListener {
                try {
                    LanaConnectionVault(this@MainActivity).forget()
                    voiceSessionToken = null
                    voiceSessionTokenAddress = null
                    findViewById<EditText>(R.id.coreToken).text.clear()
                    findViewById<EditText>(R.id.coreServerUrl).text.clear()
                    voiceDiagnostic("Сохранённое подключение удалено")
                } catch (_: Exception) {
                    voiceDiagnostic("Не удалось удалить сохранённое подключение")
                }
            }
        })
        try {
            val saved = LanaConnectionVault(this).load() ?: return
            findViewById<EditText>(R.id.coreServerUrl).setText(saved.first)
            voiceSessionTokenAddress = saved.first
            voiceSessionToken = saved.second
            voiceDiagnostic("Сохранённое подключение загружено; связь ещё не проверена")
        } catch (_: Exception) {
            voiceDiagnostic("Не удалось прочитать сохранённый ключ. Введи подключение заново")
        }
    }

'''
    s=one(s, '    private fun appendHistory(line: String) {', restore+'    private fun appendHistory(line: String) {')
    (src/'LanaConnectionCipher.kt').write_text(r'''package ai.dreibus.lanalive

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object LanaConnectionCipher {
    private val aad = "LANA connection v1".toByteArray(Charsets.UTF_8)
    fun encrypt(value: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        require(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(value)
    }
    fun decrypt(value: ByteArray, key: SecretKey): ByteArray {
        require(value.size >= 29 && value[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, value.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        return cipher.doFinal(value.copyOfRange(13, value.size))
    }
}
''')
    (src/'LanaConnectionVault.kt').write_text(r'''package ai.dreibus.lanalive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.json.JSONObject

/** Encrypted device-only storage; no secrets in preferences, backups or logs. */
internal class LanaConnectionVault(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "lana-connection.enc"))
    private val alias = "lana.connection.aes.v1"
    private fun existingKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").run {
        load(null)
        getKey(alias, null) as? SecretKey
    }
    private fun keyForSave(): SecretKey = existingKey() ?: KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        generateKey()
    }
    private fun validate(address: String, token: String) {
        val url = java.net.URL(address)
        require(url.protocol == "https" && !url.host.isNullOrBlank() && url.userInfo == null &&
            url.query == null && url.ref == null && token.length in 32..8192 &&
            token.none { it.isWhitespace() || it.isISOControl() })
    }
    fun save(address: String, token: String) {
        validate(address, token)
        val plain = JSONObject().put("address", address).put("token", token).toString().toByteArray(Charsets.UTF_8)
        val encrypted = try { LanaConnectionCipher.encrypt(plain, keyForSave()) } finally { plain.fill(0) }
        val output = file.startWrite()
        try {
            output.write(encrypted)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
    fun load(): Pair<String, String>? {
        val encrypted = try { file.openRead().use { it.readBytes() } }
            catch (_: java.io.FileNotFoundException) { return null }
        require(encrypted.size <= 32768)
        val key = existingKey() ?: throw IllegalStateException("Device key unavailable")
        val plain = LanaConnectionCipher.decrypt(encrypted, key)
        return try {
            val json = JSONObject(String(plain, Charsets.UTF_8))
            val address = json.getString("address")
            val token = json.getString("token")
            validate(address, token)
            address to token
        } finally { plain.fill(0) }
    }
    fun forget() { file.delete() }
}
''')
    (tests/'LanaConnectionCipherTest.kt').write_text(r'''package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class LanaConnectionCipherTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Test fun savesAndReloadsWithoutPlaintextAndUsesFreshNonce() {
        val key = key()
        val plain = "test-server-token-for-storage-check-only".toByteArray()
        val first = LanaConnectionCipher.encrypt(plain, key)
        val second = LanaConnectionCipher.encrypt(plain, key)
        assertFalse(first.contentEquals(second))
        assertFalse(String(first, Charsets.ISO_8859_1).contains(String(plain)))
        assertArrayEquals(plain, LanaConnectionCipher.decrypt(first, key))
        assertArrayEquals(plain, LanaConnectionCipher.decrypt(second, key))
    }
    @Test fun alteredCiphertextAndWrongDeviceKeyFailClosed() {
        val key = key()
        val encrypted = LanaConnectionCipher.encrypt("test-only".toByteArray(), key)
        val altered = encrypted.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        for ((data, decryptKey) in listOf(altered to key, encrypted to key())) {
            try { LanaConnectionCipher.decrypt(data, decryptKey); fail("Must reject invalid data") }
            catch (_: java.security.GeneralSecurityException) { }
        }
    }
    @Test fun truncatedAndUnknownFormatsFailClosed() {
        for (data in listOf(byteArrayOf(), ByteArray(28), ByteArray(40).apply { this[0] = 2 })) {
            try { LanaConnectionCipher.decrypt(data, key()); fail("Must reject format") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
''')
    clockTest=tests/'LanaDeviceClockTest.kt'
    clockTest.write_text(clockTest.read_text().replace('"Который час", at("2026-10-25T02:30:00Z")', '"Ладно сколько время", at("2026-10-25T02:30:00Z")'))
    workspace=src/'LanaWorkspace.kt'
    workspace.write_text(one(workspace.read_text(), 'Ключ доступа не сохраняется этим экраном.',
        'Адрес и ключ сохраняются на этом устройстве с шифрованием после нажатия проверки связи.'))
    p.write_text(s)
    build=out/'app/build.gradle.kts';build.write_text(build.read_text().replace('versionCode = 8','versionCode = 9').replace('0.6.3-lana-voice-repair','0.6.4-lana-shimmer'))
    return out
if __name__=='__main__':make_preview(Path(sys.argv[1]),Path(sys.argv[2]))
