package ai.dreibus.lanalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder

/** Owns recognition, the current dialogue request and audio output; UI is an attached observer. */
class LanaVoiceKeepAliveService : Service() {
    companion object { const val STOP = "ai.dreibus.lanalive.STOP_BACKGROUND_VOICE" }
    private lateinit var engine: LanaServiceRecognizer
    private lateinit var transport: LanaRuntimeTransport
    private lateinit var output: LanaRuntimeSpeaker
    private lateinit var conversation: LanaConversationRuntime
    private var runtimeOwner: String? = null
    private var runtimeEvents: ((LanaConversationRuntime.Snapshot) -> Unit)? = null
    private var historyPreferences = "LanaRuntimeHistory"
    private var savedTurn = -1L
    private var visibleHistorySaved = true
    private fun busy() = conversation.snapshot.phase in setOf(LanaConversationRuntime.Phase.THINKING,LanaConversationRuntime.Phase.PREPARING_VOICE,LanaConversationRuntime.Phase.SPEAKING)
    private fun relay(snapshot: LanaConversationRuntime.Snapshot) {
        if (snapshot.answer.isNotBlank() && savedTurn != snapshot.turn) {
            savedTurn = snapshot.turn
            visibleHistorySaved = runCatching {
                val prefs = getSharedPreferences(historyPreferences,MODE_PRIVATE)
                val lines = prefs.getString("voice_history", "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(29)
                prefs.edit().putString("voice_history",(lines + ("Лана: " + snapshot.answer)).joinToString("\n")).commit()
            }.getOrDefault(false)
        }
        runCatching { runtimeEvents?.invoke(snapshot.copy(contextSaved=snapshot.contextSaved && visibleHistorySaved)) }
            .onFailure { runtimeEvents = null; runtimeOwner = null }
        if (snapshot.turn > 0 && runtimeOwner == null && !busy()) { removeForeground(); stopSelf() }
    }
    private var foreground = false
    private var startupFailure: String? = null
    internal inner class LocalBinder : Binder() {
        fun attach(owner: String, events: LanaServiceRecognizer.Observer) {
            engine.attach(owner,events)
            startupFailure?.let { reason -> startupFailure = null; engine.close(reason) }
        }
        fun listen(owner: String,ticket: Long, events: LanaServiceRecognizer.Observer) {
            if (!engine.owns(owner)) attach(owner,events)
            engine.listen(owner,ticket)
        }
        fun cancel(owner: String) = engine.cancel(owner)
        fun report() = engine.report() + " Диалог: " + conversation.snapshot.phase.name
        fun observeConversation(owner: String, listener: (LanaConversationRuntime.Snapshot) -> Unit) {
            if (!engine.owns(owner)) return
            runtimeOwner = owner; runtimeEvents = listener; relay(conversation.snapshot)
        }
        fun conversationBusy() = busy()
        fun ask(owner: String, request: LanaRuntimeRequest, voice: LanaRuntimeVoice, ticket: Long) {
            if (!engine.owns(owner)) return
            historyPreferences = voice.historyPreferences
            engine.cancel(owner)
            conversation.ask(request,voice,ticket)
        }
        fun say(owner: String, text: String, voice: LanaRuntimeVoice, ticket: Long) {
            if (!engine.owns(owner)) return
            historyPreferences = voice.historyPreferences
            engine.cancel(owner)
            conversation.say(text,voice,ticket)
        }
        fun stopConversation(owner: String) { if (engine.owns(owner)) conversation.stop() }
        fun endBackgroundMode(owner: String) {
            if (engine.owns(owner)) { removeForeground(); stopSelf() }
        }
        fun endSession(owner: String) {
            if (engine.owns(owner)) { conversation.stop(); engine.cancel(owner); removeForeground(); stopSelf() }
        }
        fun detach(owner: String) {
            if (engine.owns(owner)) {
                engine.detach(owner); runtimeEvents = null; runtimeOwner = null
                if (!foreground || !busy()) { conversation.stop(); removeForeground(); stopSelf() }
            }
        }
    }
    private val binder = LocalBinder()
    override fun onCreate() {
        super.onCreate()
        engine = LanaServiceRecognizer(this)
        transport = LanaRuntimeTransport(this); output = LanaRuntimeSpeaker(this)
        conversation = LanaConversationRuntime(transport,output,transport::accepted)
        conversation.observe { relay(it) }
    }
    override fun onBind(intent: Intent?): IBinder = binder
    private fun removeForeground() {
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            conversation.stop()
            engine.close("Разговор остановлен из уведомления")
            removeForeground(); stopSelf(); return START_NOT_STICKY
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("lana_voice_session","Голосовой сеанс Ланы",NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this,1,Intent(this,LanaVoiceKeepAliveService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this,"lana_voice_session").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Лана: голосовой сеанс включён")
            .setContentText("Микрофон обслуживает голосовой сервис. Нажми STOP для остановки.")
            .setContentIntent(open).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null,"STOP",stop).build()).build()
        try {
            startForeground(2501,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            foreground = true
            startupFailure = null
        } catch (_: Exception) {
            val reason = "Не удалось включить фоновый голосовой сервис. Запусти разговор на открытом экране."
            startupFailure = if (engine.hasObserver()) null else reason
            conversation.stop()
            engine.close(reason)
            removeForeground(); stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onUnbind(intent: Intent?): Boolean {
        runtimeEvents = null; runtimeOwner = null
        engine.close("Голосовой экран отключён; микрофон освобождён")
        if (!foreground || !busy()) { conversation.stop(); removeForeground(); stopSelf() }
        return false
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        conversation.stop()
        engine.close("Приложение закрыто; голосовой сеанс остановлен")
        removeForeground(); stopSelf(); super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() {
        runtimeEvents = null; runtimeOwner = null
        conversation.stop(); transport.close(); output.close()
        engine.close("Голосовой сервис завершён")
        removeForeground(); super.onDestroy()
    }
}
