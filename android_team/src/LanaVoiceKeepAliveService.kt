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
    private val trace = LanaSessionTrace()
    private fun event(value: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(java.util.Date())
        trace.add("$stamp $value")
        getSharedPreferences("lana_session_trace",MODE_PRIVATE).edit().putString("events",trace.report()).apply()
    }
    private var wake: android.os.PowerManager.WakeLock? = null
    private val wakeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val renewWake = object : Runnable {
        override fun run() {
            if (!loopActive || !foreground) return
            try {
                if (wake == null) wake = (getSystemService(POWER_SERVICE) as android.os.PowerManager)
                    .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"LANA:VoiceSession").apply { setReferenceCounted(false) }
                wake?.acquire(90000L)
                wakeHandler.postDelayed(this,60000L)
            } catch (error: Exception) { event("cpu_awake_failed=" + error.javaClass.simpleName) }
        }
    }
    private fun releaseWake() {
        wakeHandler.removeCallbacksAndMessages(null)
        runCatching { if (wake?.isHeld == true) wake?.release() }; wake = null
    }
    private var backgroundTeam: LanaBackgroundTeam? = null
    private var runtimeOwner: String? = null
    private var runtimeTicket: Long? = null
    private var runtimeEvents: ((LanaConversationRuntime.Snapshot) -> Unit)? = null
    private var feedbackVoice: LanaRuntimeVoice? = null
    private var errorFeedback = false
    private var historyPreferences = "LanaRuntimeHistory"
    private var savedTurn = -1L
    private var visibleHistorySaved = true
    private fun busy() = backgroundTeam?.running == true || conversation.snapshot.phase in setOf(LanaConversationRuntime.Phase.THINKING,LanaConversationRuntime.Phase.PREPARING_VOICE,LanaConversationRuntime.Phase.SPEAKING)
    private fun relay(snapshot: LanaConversationRuntime.Snapshot) {
        if (snapshot.answer.isNotBlank() && savedTurn != snapshot.turn) {
            savedTurn = snapshot.turn
            visibleHistorySaved = runCatching {
                val prefs = getSharedPreferences(historyPreferences,MODE_PRIVATE)
                val lines = prefs.getString("voice_history", "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(29)
                prefs.edit().putString("voice_history",(lines + ("Лана: " + snapshot.answer)).joinToString("\n")).commit()
            }.getOrDefault(false)
        }
        runCatching { runtimeEvents?.invoke(snapshot.copy(clientTicket=runtimeTicket ?: snapshot.clientTicket,contextSaved=snapshot.contextSaved && visibleHistorySaved)) }
            .onFailure { runtimeEvents = null; runtimeOwner = null }
        // One local error announcement, never a provider retry or recursive speech fallback.
        if (snapshot.phase == LanaConversationRuntime.Phase.ERROR && snapshot.answer.isBlank() &&
            !snapshot.textOnly && !errorFeedback && (runtimeOwner != null || loopActive)) {
            val voice = feedbackVoice
            if (voice != null) {
                errorFeedback = true
                conversation.say(snapshot.error ?: "Ответ не получен.",
                    LanaRuntimeVoice(true,false,"","","",voice.rate,voice.historyPreferences),snapshot.clientTicket)
                return
            }
        }
        if (loopActive && snapshot.phase in setOf(LanaConversationRuntime.Phase.READY,LanaConversationRuntime.Phase.ERROR)) resumeLoop()
        if (snapshot.turn > 0 && runtimeOwner == null && !loopActive && !busy()) { removeForeground(); stopSelf() }
    }
    private val loopHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val loopOwner = "service-hands-free"
    private var loopConfig: LanaRuntimeRequest? = null
    private var loopVoice: LanaRuntimeVoice? = null
    private var sessionEnded = false
    private var loopActive = false
    private val loopGate = LanaHandsFreeGate()
    private var loopTicket = 0L
    private var awake = true
    private fun stopLoop(reason: String = "local_stop") {
        event("loop_stop=" + reason); releaseWake()
        if (reason != "handover_to_screen") runCatching { backgroundTeam?.stop() }.onFailure { event("team_interrupt_not_saved") }
        sessionEnded = true; loopActive = false; loopGate.stop(); loopHandler.removeCallbacksAndMessages(null)
        loopConfig = null; loopVoice = null; feedbackVoice = null
    }
    private fun resumeLoop() {
        if (!loopActive || !foreground || busy()) return
        val epoch = loopGate.schedule() ?: return
        loopHandler.postDelayed({
            if (loopActive && foreground && loopGate.begin(epoch) && !busy()) { loopTicket = epoch; engine.listen(loopOwner,loopTicket) }
        },500)
    }
    private fun takeOver() {
        if (!foreground || loopConfig == null || loopVoice == null) return
        runtimeOwner?.let { engine.detach(it) }
        // detach() clears the old observer without notifying it as a failed session.
        loopActive = true; loopGate.start(); event("handover_to_service foreground=$foreground"); renewWake.run()
        engine.attach(loopOwner,object : LanaServiceRecognizer.Observer {
            override fun ready(ticket: Long) { event("service_mic_ready") }
            override fun partial(ticket: Long, text: String) {}
            override fun diagnostic(ticket: Long, text: String) { event("recognizer=" + text.take(140)) }
            override fun retryPhrase(ticket: Long, message: String) {
                if (!loopActive || !loopGate.accept(ticket)) return
                val voice = loopVoice ?: return
                errorFeedback = false
                event("incomplete_phrase_not_sent")
                conversation.say(message,voice,ticket)
            }
            override fun stopped(reason: String) { stopLoop("recognizer_stop: " + reason.take(100)); conversation.stop(); removeForeground(); stopSelf() }
            override fun result(ticket: Long, text: String, latencyMs: Long?) {
                if (!loopActive || !loopGate.accept(ticket)) return
                errorFeedback = false
                val config = loopConfig ?: return
                val voice = loopVoice ?: return
                val prefs = getSharedPreferences(voice.historyPreferences,MODE_PRIVATE)
                val lines = prefs.getString("voice_history", "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(29)
                if (!prefs.edit().putString("voice_history",(lines + ("Вы: " + text)).joinToString("\n")).commit()) {
                    stopLoop(); engine.close("Не удалось сохранить реплику"); removeForeground(); stopSelf(); return
                }
                fun say(reply: String) { conversation.say(reply,voice,ticket) }
                when (LanaVoiceCommands.choose(text,awake)) {
                    LanaVoiceCommands.Action.IGNORE -> { resumeLoop(); return }
                    LanaVoiceCommands.Action.STOP -> { awake = false; say("Хорошо. Позови, когда понадоблюсь."); return }
                    LanaVoiceCommands.Action.WAKE -> { awake = true; say(LanaGreetings.next()); return }
                    LanaVoiceCommands.Action.ASK -> awake = true
                }
                if (LanaLocalWorkspace.matches(text)) {
                    val memory = getSharedPreferences("lana_workspace_v1",MODE_PRIVATE)
                    val reply = runCatching {
                        LanaLocalWorkspace(memory.getString("records", "{\"tasks\":[],\"notes\":[]}")!!) {
                            memory.edit().putString("records",it).commit()
                        }.handle(text)
                    }.getOrNull() ?: "Не удалось выполнить команду локальной памяти. Проверь записи на экране."
                    say(reply); return
                }
                LanaDeviceClock.answer(text)?.let { say(it); return }
                LanaTeamVoice.localReply(text)?.let { say(it); return }
                // Local journal reads do not contact a model or resend a task.
                if (LanaCoordinator.handles(text)) {
                    val local = try {
                        LanaCoordinator.reply(text, LanaTeamJournalStore.open(this@LanaVoiceKeepAliveService).records())
                    } catch (_: Exception) {
                        "Не удалось прочитать журнал команды. Записи не изменены."
                    }
                    if (local != null) { say(local); return }
                }
                val task = try {
                    LanaTeamVoice.task(text) ?: if (LanaCoordinator.handles(text))
                        LanaCoordinator.followUp(text, LanaTeamJournalStore.open(this@LanaVoiceKeepAliveService).records()) else null
                } catch (_: Exception) {
                    say("Не удалось подготовить уточнение. Проверь журнал команды; ничего не отправлено."); return
                }
                if (config.address.isBlank() || config.token.isBlank()) {
                    say("Сначала подключи мой сервер на экране проверки связи."); return
                }
                if (task != null) {
                    try {
                        val prompt = LanaTeamPrompt.build(task)
                        val dispatcher = backgroundTeam ?: LanaBackgroundTeam(
                            LanaTeamJournalStore.open(this@LanaVoiceKeepAliveService),transport).also { backgroundTeam = it }
                        val request = LanaRuntimeRequest(config.address,config.token,java.util.UUID.randomUUID().toString(),
                            task,prompt,config.legacy,LanaWorkspace.contextNotes(this@LanaVoiceKeepAliveService))
                        dispatcher.submit(task,request) { reply ->
                            if (loopActive || runtimeOwner != null) { errorFeedback = false; conversation.say(reply,voice,ticket) }
                            else if (!busy()) { removeForeground(); stopSelf() }
                        }
                        event("team_task_dispatch running=${dispatcher.running}")
                    } catch (_: Exception) {
                        say("Задание не отправлено: проверь длину, подключение и журнал. Повторное или параллельное задание блокируется.")
                    }
                    return
                }
                if (LanaCoordinator.handles(text) || LanaTeamVoice.wantsLastReply(text)) {
                    say("Для изменения записей команды открой её экран. Ничего не отправлено."); return
                }
                val prompt = runCatching { LanaDialoguePrompt.build(text) }.getOrNull()
                if (prompt == null) { say("Вопрос слишком длинный. Раздели его на несколько реплик."); return }
                conversation.ask(LanaRuntimeRequest(config.address,config.token,config.conversationId,text,prompt,
                    config.legacy,LanaWorkspace.contextNotes(this@LanaVoiceKeepAliveService)),voice,ticket)
            }
        })
        resumeLoop()
    }
    private var foreground = false
    private var startupFailure: String? = null
    internal inner class LocalBinder : Binder() {
        fun sessionEnded() = sessionEnded
        fun configureHandsFree(owner: String, request: LanaRuntimeRequest, voice: LanaRuntimeVoice, active: Boolean) {
            if (!engine.owns(owner) || loopActive) return
            sessionEnded = false
            val restored = LanaSessionConnection.resolve(request.address,request.token,
                runCatching { LanaConnectionVault(this@LanaVoiceKeepAliveService).load() }.getOrNull())
            loopConfig = LanaRuntimeRequest(restored.first,restored.second,request.conversationId,request.question,
                request.prompt,request.legacy,request.notes)
            val voiceToken = if (voice.token.isBlank() && voice.address.trim() == restored.first) restored.second else voice.token
            val restoredVoice = LanaRuntimeVoice(voice.enabled,voice.cloud,voice.address,voiceToken,
                voice.voiceName,voice.rate,voice.historyPreferences)
            loopVoice = restoredVoice; feedbackVoice = restoredVoice; awake = active
            event("connection_configured address=${restored.first.isNotBlank()} credential=${restored.second.isNotBlank()}")
        }
        fun attach(owner: String, events: LanaServiceRecognizer.Observer): Boolean {
            val recovering = loopActive
            if (loopActive) { stopLoop("handover_to_screen"); engine.detach(loopOwner) }
            engine.attach(owner,events)
            startupFailure?.let { reason -> startupFailure = null; engine.close(reason); return false }
            return recovering
        }
        fun listen(owner: String,ticket: Long, events: LanaServiceRecognizer.Observer) {
            if (!engine.owns(owner)) attach(owner,events)
            engine.listen(owner,ticket)
        }
        fun cancel(owner: String) = engine.cancel(owner)
        fun report() = engine.report() + " Диалог: " + conversation.snapshot.phase.name +
            "\nForeground=$foreground; сервисный цикл=$loopActive; CPU удерживается=${wake?.isHeld == true}; завершён=$sessionEnded\n" + trace.report()
        fun observeConversation(owner: String, ticket: Long? = null, listener: (LanaConversationRuntime.Snapshot) -> Unit) {
            if (!engine.owns(owner)) return
            runtimeOwner = owner; runtimeTicket = ticket; runtimeEvents = listener; relay(conversation.snapshot)
        }
        fun conversationBusy() = busy()
        fun ask(owner: String, request: LanaRuntimeRequest, voice: LanaRuntimeVoice, ticket: Long) {
            if (!engine.owns(owner)) return
            historyPreferences = voice.historyPreferences
            feedbackVoice = voice; errorFeedback = false
            engine.cancel(owner)
            conversation.ask(request,voice,ticket)
        }
        fun say(owner: String, text: String, voice: LanaRuntimeVoice, ticket: Long) {
            if (!engine.owns(owner)) return
            historyPreferences = voice.historyPreferences
            feedbackVoice = voice; errorFeedback = false
            engine.cancel(owner)
            conversation.say(text,voice,ticket)
        }
        fun stopConversation(owner: String) { if (engine.owns(owner)) { stopLoop(); conversation.stop() } }
        fun endBackgroundMode(owner: String) {
            if (engine.owns(owner)) { stopLoop("button_or_background_setting_STOP"); conversation.stop(); engine.cancel(owner); removeForeground(); stopSelf() }
        }
        fun endSession(owner: String) {
            if (engine.owns(owner)) { stopLoop("button_or_background_setting_STOP"); conversation.stop(); engine.cancel(owner); removeForeground(); stopSelf() }
        }
        fun detach(owner: String) {
            if (engine.owns(owner)) {
                runtimeEvents = null; runtimeOwner = null
                if (foreground && loopConfig != null && loopVoice != null) { engine.detach(owner); takeOver() }
                else {
                    engine.detach(owner)
                    if (!foreground || !busy()) { stopLoop("screen_detached_without_foreground"); conversation.stop(); removeForeground(); stopSelf() }
                }
            }
        }
    }
    private val binder = LocalBinder()
    override fun onCreate() {
        super.onCreate()
        getSharedPreferences("lana_session_trace",MODE_PRIVATE).getString("events", "").orEmpty()
            .lineSequence().filter { it.isNotBlank() && it.length <= 180 }.take(24).forEach { trace.add(it) }
        event("service_created")
        engine = LanaServiceRecognizer(this)
        transport = LanaRuntimeTransport(this,::event); output = LanaRuntimeSpeaker(this)
        conversation = LanaConversationRuntime(transport,output,transport::accepted)
        conversation.observe { relay(it) }
    }
    override fun onBind(intent: Intent?): IBinder = binder
    private fun removeForeground() {
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopLoop("notification_STOP"); conversation.stop()
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
            foreground = true; event("foreground_started")
            startupFailure = null
        } catch (error: Exception) {
            event("foreground_failed=" + error.javaClass.simpleName)
            val reason = "Не удалось включить фоновый голосовой сервис. Запусти разговор на открытом экране."
            startupFailure = if (engine.hasObserver()) null else reason
            stopLoop(); conversation.stop()
            engine.close(reason)
            removeForeground(); stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onUnbind(intent: Intent?): Boolean {
        runtimeEvents = null; runtimeOwner = null
        if (!loopActive) {
            engine.close("Голосовой экран отключён; микрофон освобождён")
            if (!foreground || !busy()) { stopLoop("screen_detached_without_foreground"); conversation.stop(); removeForeground(); stopSelf() }
        }
        return false
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopLoop("task_removed"); conversation.stop()
        engine.close("Приложение закрыто; голосовой сеанс остановлен")
        removeForeground(); stopSelf(); super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() {
        runtimeEvents = null; runtimeOwner = null
        stopLoop("service_destroyed"); conversation.stop(); transport.close(); output.close()
        engine.close("Голосовой сервис завершён")
        removeForeground(); super.onDestroy()
    }
}
