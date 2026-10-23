package ai.dreibus.lanalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Experimental session continuity only. Recognition still belongs to the Activity. */
class LanaVoiceKeepAliveService : Service() {
    companion object {
        const val STOP = "ai.dreibus.lanalive.STOP_BACKGROUND_VOICE"
        var onStopRequested: (() -> Unit)? = null
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { onStopRequested?.invoke(); stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("lana_voice_session", "Голосовой сеанс Ланы", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, LanaVoiceKeepAliveService::class.java).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "lana_voice_session") else Notification.Builder(this)
        val notification = builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Лана: голосовой сеанс включён")
            .setContentText("Экспериментальный фон. Нажми STOP для остановки.")
            .setContentIntent(open).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null, "STOP", stop).build()).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(2501, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(2501, notification)
        } catch (_: Exception) { onStopRequested?.invoke(); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) { onStopRequested?.invoke(); stopSelf(); super.onTaskRemoved(rootIntent) }
}
