package ai.dreibus.lanalive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Owned by a Service, never by a View or Activity. All calls and callbacks use the main looper. */
internal class LanaServiceRecognizer(private val context: Context) {
    interface Observer {
        fun ready(ticket: Long)
        fun partial(ticket: Long, text: String)
        fun diagnostic(ticket: Long, text: String)
        fun result(ticket: Long, text: String, latencyMs: Long?)
        fun retryPhrase(ticket: Long, message: String)
        fun stopped(reason: String)
    }
    private val gate = LanaRecognitionLease()
    private val handler = Handler(Looper.getMainLooper())
    private val continuationHandler = Handler(Looper.getMainLooper())
    private val assembly = LanaSpeechAssembly()
    private val phraseRecovery = LanaPhraseRecovery()
    private var finalEndedAt: Long? = null
    private val recovery = LanaRecognitionRecovery()
    private var recognizer: SpeechRecognizer? = null
    private var observer: Observer? = null
    private var owner: String? = null
    private var turn: LanaRecognitionTurn? = null
    private fun mainThread() { check(Looper.myLooper() == Looper.getMainLooper()) }
    fun owns(client: String) = gate.owns(client)
    fun hasObserver() = observer != null
    fun report() = recovery.report()
    fun attach(client: String, events: Observer) {
        mainThread()
        val previous = observer
        val changedOwner = !gate.owns(client)
        clearAssembly()
        clearAttempt(destroy = true)
        if (changedOwner) phraseRecovery.accepted()
        gate.attach(client); owner = client; observer = events
        if (previous !== events) previous?.stopped("Голосовой сеанс передан новому экрану")
    }
    fun cancel(client: String) {
        mainThread()
        if (gate.cancel(client)) { clearAssembly(); clearAttempt(destroy = false) }
    }
    fun detach(client: String) {
        mainThread()
        if (gate.detach(client)) { clearAssembly(); observer = null; owner = null; clearAttempt(destroy = true) }
    }
    fun close(reason: String) {
        mainThread()
        val events = observer
        owner?.let { gate.detach(it) }; owner = null; observer = null
        clearAssembly()
        clearAttempt(destroy = true)
        events?.stopped(reason)
    }
    private fun clearAssembly() {
        continuationHandler.removeCallbacksAndMessages(null)
        assembly.clear(); finalEndedAt = null
    }
    private fun deliver(client: String, ticket: Long, appTicket: Long, stopText: String? = null) {
        if (!gate.accepts(client,ticket)) return
        val text = stopText ?: assembly.take()
        if (stopText == null && LanaIncompleteSpeech.missingFirstOperand(text)) {
            rejectPhrase(client,ticket,appTicket,"Не расслышала первое число. Повтори вопрос целиком, я слушаю.")
            return
        }
        if (!gate.finish(client,ticket)) return
        val latency = finalEndedAt?.let { SystemClock.elapsedRealtime() - it }
        clearAssembly(); clearAttempt(destroy = false)
        if (text.isNotBlank()) { phraseRecovery.accepted(); recovery.successfulPhrase(); observer?.result(appTicket,text,latency) }
    }
    private fun scheduleDelivery(client: String, ticket: Long, appTicket: Long) {
        continuationHandler.removeCallbacksAndMessages(null)
        val wait = assembly.remaining(SystemClock.elapsedRealtime()) ?: return
        continuationHandler.postDelayed({
            if (gate.accepts(client,ticket) && assembly.ready(SystemClock.elapsedRealtime())) deliver(client,ticket,appTicket)
        }, wait)
    }
    private fun clearAttempt(destroy: Boolean, cancelCurrent: Boolean = true) {
        turn?.finish(); turn = null
        handler.removeCallbacksAndMessages(null)
        // onResults is already final; avoid racing the next recognition with a redundant cancel.
        if (cancelCurrent) runCatching { recognizer?.cancel() }
        if (destroy) { runCatching { recognizer?.destroy() }; recognizer = null }
    }
    fun listen(client: String, appTicket: Long) {
        mainThread()
        if (!gate.owns(client)) return
        clearAssembly()
        clearAttempt(destroy = false)
        val ticket = gate.begin(client) ?: return
        recovery.reset()
        attempt(client, ticket, appTicket)
    }
    private fun attempt(client: String, ticket: Long, appTicket: Long) {
        if (!gate.accepts(client,ticket)) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            failed(client,ticket,appTicket,9); return
        }
        val current = LanaRecognitionTurn(); turn = current
        var ready = false
        fun live() = gate.accepts(client,ticket) && current.acceptsEvents()
        fun error(code: Int, origin: String = "android") {
            if (live() && code != 6 && code != 7) observer?.diagnostic(appTicket,"Распознавание: источник=$origin; код=$code")
            if (gate.accepts(client,ticket) && current.finish()) failed(client,ticket,appTicket,code)
        }
        handler.postDelayed({ if (live() && !ready) error(11,"start_timeout") }, LanaRecognitionRecovery.START_TIMEOUT_MS)
        handler.postDelayed({ if (live()) error(11,"turn_timeout") }, LanaRecognitionRecovery.TURN_TIMEOUT_MS)
        try {
            if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer!!.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!live()) return
                    ready = true; observer?.ready(appTicket)
                }
                override fun onBeginningOfSpeech() {
                    if (!live()) return
                    ready = true; current.speechBegan(); assembly.speechBegan()
                    continuationHandler.removeCallbacksAndMessages(null)
                    observer?.ready(appTicket)
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (!live() || !current.endOfSpeech(SystemClock.elapsedRealtime())) return
                    handler.postDelayed({
                        if (gate.accepts(client,ticket) && current.timeout(SystemClock.elapsedRealtime())) run { observer?.diagnostic(appTicket,"Распознавание: источник=final_timeout; код=11"); failed(client,ticket,appTicket,11) }
                    }, LanaRecognitionTurn.FINAL_TIMEOUT_MS)
                }
                override fun onPartialResults(results: Bundle?) {
                    if (!live()) return
                    val preview = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (preview.isNotBlank()) {
                        assembly.speechBegan(); continuationHandler.removeCallbacksAndMessages(null)
                        observer?.partial(appTicket,preview.take(160))
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(code: Int) { error(code) }
                override fun onResults(results: Bundle?) {
                    if (!live() || !current.finish()) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isBlank()) { failed(client,ticket,appTicket,7); return }
                    if (LanaVoiceCommands.choose(text,true) == LanaVoiceCommands.Action.STOP) {
                        deliver(client,ticket,appTicket,text); return
                    }
                    finalEndedAt = current.endedAt
                    if (!assembly.segment(text,SystemClock.elapsedRealtime())) {
                        clearAssembly(); gate.finish(client,ticket); clearAttempt(destroy = false)
                        observer?.stopped("Фраза слишком длинная. Вопрос не отправлен; раздели его на части."); return
                    }
                    observer?.diagnostic(appTicket,"Фрагмент получен; слушаю продолжение перед отправкой")
                    if (!gate.accepts(client,ticket)) return
                    clearAttempt(destroy = false, cancelCurrent = false)
                    scheduleDelivery(client,ticket,appTicket)
                    handler.postDelayed({ if (gate.accepts(client,ticket)) attempt(client,ticket,appTicket) },300L)
                }
            })
            recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,LanaRecognitionTurn.TARGET_SILENCE_MS)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,LanaRecognitionTurn.INCOMPLETE_SILENCE_MS)
            })
        } catch (_: Exception) { error(3) }
    }
    private fun rejectPhrase(client: String, ticket: Long, appTicket: Long, message: String) {
        if (!gate.finish(client,ticket)) return
        clearAssembly(); clearAttempt(destroy = true)
        if (phraseRecovery.retryAllowed()) observer?.retryPhrase(appTicket,message)
        else observer?.stopped("Несколько фраз подряд не распознаны целиком. Сеанс остановлен; можно использовать текстовый ввод.")
    }
    private fun failed(client: String, ticket: Long, appTicket: Long, code: Int) {
        if (!gate.accepts(client,ticket)) return
        if (code in setOf(9,12,13)) clearAssembly()
        if (assembly.hasText) {
            if (assembly.continuing) {
                rejectPhrase(client,ticket,appTicket,"Не расслышала окончание. Вопрос не отправлен. Повтори его целиком, я слушаю.")
            } else if (code == 6 || code == 7) {
                clearAttempt(destroy = false)
                scheduleDelivery(client,ticket,appTicket)
            } else {
                rejectPhrase(client,ticket,appTicket,"Распознавание прервалось. Вопрос не отправлен. Повтори его целиком, я слушаю.")
            }
            return
        }
        val decision = recovery.error(code)
        clearAttempt(destroy = decision.recreate)
        if (decision.diagnostic) observer?.diagnostic(appTicket,decision.message)
        // A diagnostic can cause a client to stop/detach. Never re-enable that request.
        if (!gate.accepts(client,ticket)) return
        if (decision.stop) {
            gate.finish(client,ticket)
            observer?.stopped(decision.message)
        } else handler.postDelayed({ if (gate.accepts(client,ticket)) attempt(client,ticket,appTicket) },decision.delayMs)
    }
}
