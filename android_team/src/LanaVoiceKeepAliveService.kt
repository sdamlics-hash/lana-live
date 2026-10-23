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

/** Owns speech recognition and its retries. Conversation requests/TTS still belong to the attached screen. */
class LanaVoiceKeepAliveService : Service() {
    companion object { const val STOP = "ai.dreibus.lanalive.STOP_BACKGROUND_VOICE" }
    private lateinit var engine: LanaServiceRecognizer
    private var foreground = false
    private var startupFailure: String? = null
    internal inner class LocalBinder : Binder() {
        fun attach(owner: String, events: LanaServiceRecognizer.Observer) {
            engine.attach(owner,events)
            startupFailure?.let { reason -> startupFailure = null; engine.close(reason) }
        }
        fun listen(owner: String,ticket: Long, events: LanaServiceRecognizer.Observer) {
            if (!engine.owns(owner)) engine.attach(owner,events)
            engine.listen(owner,ticket)
        }
        fun cancel(owner: String) = engine.cancel(owner)
        fun report() = engine.report()
        fun endBackgroundMode(owner: String) {
            if (engine.owns(owner)) { removeForeground(); stopSelf() }
        }
        fun endSession(owner: String) {
            if (engine.owns(owner)) { engine.cancel(owner); removeForeground(); stopSelf() }
        }
        fun detach(owner: String) {
            if (engine.owns(owner)) { engine.detach(owner); removeForeground(); stopSelf() }
        }
    }
    private val binder = LocalBinder()
    override fun onCreate() { super.onCreate(); engine = LanaServiceRecognizer(this) }
    override fun onBind(intent: Intent?): IBinder = binder
    private fun removeForeground() {
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
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
            startupFailure = reason
            engine.close(reason)
            removeForeground(); stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onUnbind(intent: Intent?): Boolean {
        engine.close("Голосовой экран отключён; микрофон освобождён")
        removeForeground(); stopSelf()
        return false
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        engine.close("Приложение закрыто; голосовой сеанс остановлен")
        removeForeground(); stopSelf(); super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() {
        engine.close("Голосовой сервис завершён")
        removeForeground(); super.onDestroy()
    }
}
