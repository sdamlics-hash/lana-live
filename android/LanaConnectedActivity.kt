package ai.dreibus.lanalive

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class LanaConnectedActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var address: EditText
    private lateinit var token: EditText
    private lateinit var listen: Button
    private lateinit var auto: CheckBox
    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var busy = false
    private val history = JSONArray()
    private val prefs by lazy { getSharedPreferences("lana_config", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 20)
        }
        val title = TextView(this).apply { text = "ЛАНА · ЖИВОЙ ДИАЛОГ"; textSize = 23f }
        status = TextView(this).apply { text = "Подключи адрес сервера и нажми «Говорить»"; textSize = 16f }
        address = EditText(this).apply {
            hint = "https://адрес-сервера"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString("address", "https://claimed-cornell-trades-surge.trycloudflare.com"))
        }
        token = EditText(this).apply {
            hint = "Ключ доступа к Лане"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("token", ""))
        }
        val save = Button(this).apply { text = "Сохранить подключение" }
        listen = Button(this).apply { text = "Говорить с Ланой" }
        auto = CheckBox(this).apply { text = "Продолжать слушать после ответа" }
        log = TextView(this).apply { text = "История разговора:\n"; textSize = 16f }
        val scroll = ScrollView(this)
        scroll.addView(log)
        for (v in listOf(title, status, address, token, save, listen, auto)) {
            column.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        column.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(column)
        save.setOnClickListener {
            prefs.edit().putString("address", address.text.toString().trim().trimEnd('/'))
                .putString("token", token.text.toString().trim()).apply()
            status.text = "Подключение сохранено"
        }
        listen.setOnClickListener { startListening() }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speech = SpeechRecognizer.createSpeechRecognizer(this)
            speech?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "Слушаю..." }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "Распознаю..." }
                override fun onError(error: Int) {
                    busy = false
                    status.text = "Ошибка микрофона: $error. Нажми «Говорить» ещё раз."
                }
                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (words.isBlank()) { busy = false; status.text = "Не расслышала"; return }
                    askLana(words)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else status.text = "На устройстве недоступно распознавание речи"
        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale("ru", "RU")
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                ui.post {
                    busy = false
                    if (auto.isChecked && !isFinishing) ui.postDelayed({ startListening() }, 500)
                }
            }
            override fun onError(utteranceId: String?) { ui.post { busy = false } }
        })
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    private fun startListening() {
        if (busy) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100); return
        }
        busy = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try { speech?.startListening(intent) ?: run { busy = false } }
        catch (e: Exception) { busy = false; status.text = "Микрофон: ${e.message}" }
    }

    private fun askLana(words: String) {
        log.append("\nТы: $words\n")
        status.text = "Лана думает..."
        val endpoint = address.text.toString().trim().trimEnd('/')
        val bearer = token.text.toString().trim()
        prefs.edit().putString("address", endpoint).putString("token", bearer).apply()
        if (!endpoint.startsWith("https://") || bearer.isBlank()) {
            status.text = "Нужны HTTPS-адрес и ключ доступа"
            busy = false
            return
        }
        thread {
            var connection: HttpURLConnection? = null
            try {
                val payload = JSONObject().put("message", words).put("history", history).toString()
                connection = URL("$endpoint/v1/lana/chat").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 12000
                connection.readTimeout = 45000
                connection.setRequestProperty("Authorization", "Bearer $bearer")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) throw Exception("Сервер вернул HTTP $code")
                val reply = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    .optString("reply").ifBlank { "Не получила ответ." }
                ui.post {
                    history.put(JSONObject().put("role", "user").put("content", words))
                    history.put(JSONObject().put("role", "assistant").put("content", reply))
                    log.append("Лана: $reply\n")
                    status.text = "Лана отвечает"
                    if (ttsReady) {
                        val codeTts = tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "lana_reply")
                        if (codeTts != TextToSpeech.SUCCESS) { busy = false; status.text = "Не удалось воспроизвести голос" }
                    } else { busy = false; status.text = "Нет русского голосового движка" }
                }
            } catch (e: Exception) {
                ui.post { busy = false; status.text = "Ошибка соединения: ${e.message}" }
            } finally { connection?.disconnect() }
        }
    }

    override fun onDestroy() {
        speech?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
