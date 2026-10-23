package ai.dreibus.lanalive

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Service-owned output. No Activity context, raw key logging, audio reuse, or automatic paid retries. */
internal class LanaRuntimeSpeaker(private val context: Context) : LanaConversationRuntime.Speaker {
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,ArrayBlockingQueue(2),{ task -> Thread(task,"LanaVoiceOutput").apply { isDaemon = true } })
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private var focus: android.media.AudioFocusRequest? = null
    private fun acquireFocus(): Boolean {
        val focusGeneration = generation
        val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ change ->
                if (generation == focusGeneration && change < 0 && done != null) finish("Озвучка остановлена другим звуком. Ответ доступен текстом.")
            },main).build()
        if (audioManager.requestAudioFocus(request) != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = request
        return true
    }
    private var generation = 0L
    private var player: MediaPlayer? = null
    private var clip: File? = null
    private var future: java.util.concurrent.Future<*>? = null
    private var tts: TextToSpeech? = null
    private var ready: Boolean? = null
    private var waiting: (() -> Unit)? = null
    private var done: ((String?) -> Unit)? = null
    private var onStarted: (() -> Unit)? = null
    private var utterance: String? = null
    init {
        tts = TextToSpeech(context) { code ->
            ready = code == TextToSpeech.SUCCESS && (tts?.setLanguage(Locale("ru","RU")) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
            val pending = waiting; waiting = null
            try { pending?.invoke() } catch (_: Exception) { finish("Системный голос не запустился. Ответ доступен текстом.") }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { main.post { if (id != null && id == utterance) onStarted?.invoke() } }
            override fun onDone(id: String?) { main.post { if (id != null && id == utterance) finish(null) } }
            override fun onError(id: String?) { main.post { if (id != null && id == utterance) finish("Ошибка системной озвучки. Ответ доступен текстом.") } }
        })
    }
    private fun releaseOutput() {
        future?.cancel(true); future = null
        waiting = null; utterance = null
        runCatching { player?.release() }; player = null
        clip?.delete(); clip = null
        runCatching { tts?.stop() }
        focus?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }; focus = null
        main.removeCallbacksAndMessages(null)
    }
    private fun cancelOutput() { generation++; done = null; onStarted = null; releaseOutput() }
    private fun finish(error: String?) {
        val callback = done ?: return
        done = null; onStarted = null
        releaseOutput()
        callback(error)
    }
    override fun speak(text: String, voice: LanaRuntimeVoice, started: () -> Unit, complete: (String?) -> Unit): LanaConversationRuntime.Cancel {
        cancelOutput()
        val spoken = LanaSpokenText.clean(text)
        if (spoken.isBlank()) { complete(null); return LanaConversationRuntime.Cancel {} }
        val id = generation
        done = complete; onStarted = started
        main.postDelayed({ if (generation == id && done != null) finish("Озвучка не завершилась вовремя. Ответ доступен текстом.") },120000)
        if (voice.cloud) cloud(id,spoken,voice) else system(id,spoken,voice)
        return LanaConversationRuntime.Cancel { if (generation == id) cancelOutput() }
    }
    private fun system(id: Long, text: String, voice: LanaRuntimeVoice) {
        fun runVoice() {
            if (generation != id || done == null) return
            if (ready != true) { finish("Русский системный голос недоступен. Ответ доступен текстом."); return }
            val engine = tts ?: return
            engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            val selected = engine.voices.orEmpty().firstOrNull { it.name == voice.voiceName && it.locale.language == "ru" }
            if (selected != null) engine.voice = selected else engine.setLanguage(Locale("ru","RU"))
            engine.setSpeechRate(voice.rate.coerceIn(0.6f,1.4f)); engine.setPitch(1f)
            if (!acquireFocus()) { finish("Звук занят другим приложением. Ответ доступен текстом."); return }
            val key = "lana_runtime_$id"; utterance = key
            if (engine.speak(text,TextToSpeech.QUEUE_FLUSH,null,key) != TextToSpeech.SUCCESS) finish("Системный голос не запустился. Ответ доступен текстом.")
        }
        if (ready == null) {
            waiting = { runVoice() }
            main.postDelayed({ if (generation == id && done != null && waiting != null) finish("Системный голос не подготовился вовремя. Ответ доступен текстом.") },8000)
        } else runVoice()
    }
    private fun cloud(id: Long, text: String, voice: LanaRuntimeVoice) {
        if (voice.address.isBlank() || voice.token.length < 32) { finish("Голос не подключён. Ответ доступен текстом."); return }
        future = try { worker.submit {
            val result = runCatching { LanaSpeechClient.audio(voice.address,voice.token,text) }
            main.post {
                if (generation != id || done == null) return@post
                val bytes = result.getOrNull()
                if (bytes == null) { finish("Голосовой ответ не получен. Ответ доступен текстом; автоматического повтора нет."); return@post }
                try {
                    val file = File.createTempFile("lana-runtime-voice-",".mp3",context.cacheDir); clip = file; file.writeBytes(bytes)
                    val audio = MediaPlayer(); player = audio
                    var prepared = false
                    audio.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    audio.setOnPreparedListener {
                        if (generation == id && player === audio && done != null) {
                            try {
                                prepared = true
                                if (!acquireFocus()) finish("Звук занят другим приложением. Ответ доступен текстом.")
                                else { audio.start(); onStarted?.invoke() }
                            } catch (_: Exception) { finish("Не удалось запустить проигрыватель. Ответ доступен текстом.") }
                        }
                    }
                    audio.setOnCompletionListener { if (generation == id && player === audio) finish(null) }
                    audio.setOnErrorListener { _, _, _ ->
                        if (generation == id && player === audio) finish("Не удалось воспроизвести ответ. Ответ доступен текстом.")
                        true
                    }
                    audio.setDataSource(file.absolutePath); audio.prepareAsync()
                    main.postDelayed({ if (generation == id && player === audio && !prepared) finish("Проигрыватель не подготовился вовремя. Ответ доступен текстом.") },10000)
                } catch (_: Exception) { finish("Не удалось подготовить озвучку. Ответ доступен текстом.") }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { finish("Предыдущая озвучка ещё завершается. Ответ доступен текстом."); null }
    }
    fun close() { cancelOutput(); worker.shutdownNow(); tts?.shutdown(); tts = null }
}
