from pathlib import Path

def install(out: Path, root: Path):
    src = out/'app/src/main/java/ai/dreibus/lanalive'
    tests = out/'app/src/test/java/ai/dreibus/lanalive'
    for name in ('LanaPhraseRecovery.kt', 'LanaSpeechAssembly.kt', 'LanaIncompleteSpeech.kt', 'LanaServiceRecognizer.kt', 'LanaRecognitionLease.kt', 'LanaVoiceKeepAliveService.kt'):
        (src/name).write_text((root/'android_team/src'/name).read_text())
    (tests/'LanaPhraseRecoveryTest.kt').write_text((root/'android_team/tests/LanaPhraseRecoveryTest.kt').read_text())
    (tests/'LanaSpeechAssemblyTest.kt').write_text((root/'android_team/tests/LanaSpeechAssemblyTest.kt').read_text())
    (tests/'LanaIncompleteSpeechTest.kt').write_text((root/'android_team/tests/LanaIncompleteSpeechTest.kt').read_text())
    (tests/'LanaRecognitionLeaseTest.kt').write_text((root/'android_team/tests/LanaRecognitionLeaseTest.kt').read_text())
    p = src/'MainActivity.kt'
    s = p.read_text()
    def change(old,new):
        nonlocal s
        if s.count(old) != 1: raise ValueError('Service anchor not unique: '+old[:90])
        s = s.replace(old,new)
    # Reuse the application's existing final-text command routing, without any microphone objects.
    a = s.index('                    status.text = "Услышала: $words"')
    b = s.index('\n                }\n            })',a)
    final = s[a:b].replace('                    recognitionRecovery.successfulPhrase()\n','')
    service_fields = '''    private val recognitionOwner = java.util.UUID.randomUUID().toString()
    private var recognitionBinder: LanaVoiceKeepAliveService.LocalBinder? = null
    private var recognitionBindingRequested = false
    private var recognitionPending = false
    private val recognitionObserver = object : LanaServiceRecognizer.Observer {
        private fun live(ticket: Long) = voiceEnabled && voiceSession.accepts(ticket) && !isFinishing && !isDestroyed
        override fun ready(ticket: Long) {
            if (live(ticket)) status.text = if (conversationActive) "Слушаю" else "Слушаю: скажи «Лана, ты тут?»"
        }
        override fun partial(ticket: Long, text: String) {
            if (live(ticket)) status.text = "Слушаю: " + text
        }
        override fun diagnostic(ticket: Long, text: String) {
            if (live(ticket)) { status.text = text; voiceDiagnostic(text) }
        }
        override fun result(ticket: Long, words: String, latencyMs: Long?) {
            if (!live(ticket)) return
            latencyMs?.let { voiceDiagnostic("Конец речи → текст, мс: $it") }
FINAL_BODY
        }
        override fun retryPhrase(ticket: Long, message: String) {
            if (!live(ticket)) return
            voiceDiagnostic(message)
            speakFromLana(message)
        }
        override fun stopped(reason: String) {
            if (isFinishing || isDestroyed) return
            if (voiceEnabled) findViewById<Button>(R.id.voiceButton).performClick()
            status.text = reason
            voiceDiagnostic(reason)
        }
    }
    private val recognitionConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            if (!recognitionBindingRequested) return
            recognitionBinder = service as? LanaVoiceKeepAliveService.LocalBinder
            recognitionBinder?.attach(recognitionOwner,recognitionObserver)
            if (recognitionPending && voiceEnabled && !speaking) listenOnce()
        }
        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            recognitionBinder = null
            recognitionPending = false
            if (recognitionBindingRequested) {
                runCatching { unbindService(this) }
                recognitionBindingRequested = false
            }
            recognitionObserver.stopped("Связь с голосовым сервисом потеряна. Нажми «Слушать», чтобы начать новый сеанс.")
        }
        override fun onBindingDied(name: android.content.ComponentName?) { onServiceDisconnected(name) }
        override fun onNullBinding(name: android.content.ComponentName?) { onServiceDisconnected(name) }
    }
    private fun bindRecognitionService() {
        if (recognitionBindingRequested) return
        recognitionBindingRequested = try {
            bindService(Intent(this,LanaVoiceKeepAliveService::class.java),recognitionConnection,android.content.Context.BIND_AUTO_CREATE)
        } catch (_: Exception) { false }
        if (!recognitionBindingRequested) recognitionObserver.stopped("Не удалось подключить голосовой сервис. Можно использовать текстовый ввод.")
    }
    internal fun disableBackgroundRecognition() { recognitionBinder?.endBackgroundMode(recognitionOwner) }
    private fun listenOnce() {
        if (!voiceEnabled || speaking) return
        val binder = recognitionBinder
        if (binder == null) {
            recognitionPending = true
            status.text = "Подключаю голосовой сервис…"
            bindRecognitionService()
            val waitTicket = voiceSession.current()
            handler.postDelayed({
                if (recognitionPending && recognitionBinder == null && voiceEnabled && voiceSession.accepts(waitTicket)) {
                    if (recognitionBindingRequested) runCatching { unbindService(recognitionConnection) }
                    recognitionBindingRequested = false
                    recognitionObserver.stopped("Голосовой сервис не подключился. Начни новый сеанс или используй текстовый ввод.")
                }
            },12000)
            return
        }
        recognitionPending = false
        val ticket = voiceSession.start()
        status.text = "Подключаю микрофон…"
        binder.listen(recognitionOwner,ticket,recognitionObserver)
    }
'''.replace('FINAL_BODY',final)
    a = s.index('    private val recognitionRecovery = LanaRecognitionRecovery()')
    b = s.index('    private fun retryVoice()',a)
    s = s[:a]+service_fields+'\n'+s[b:]
    change('    private var recognizer: SpeechRecognizer? = null\n','')
    a = s.index('    private var activeRecognitionTurn: LanaRecognitionTurn? = null')
    b = s.index('    private var retryCount = 0',a)
    s = s[:a]+'''    private fun cancelRecognition() {
        recognitionPending = false
        recognitionBinder?.cancel(recognitionOwner)
    }
'''+s[b:]
    change('        scheduleRecognitionRecovery(delay)', '''        val ticket = voiceSession.current()
        handler.postDelayed({ if (voiceSession.accepts(ticket) && voiceEnabled && !speaking) listenOnce() },delay)''')
    s = s.replace(' + recognitionRecovery.report() +', ' + (recognitionBinder?.report() ?: "Голосовой сервис не подключён") +')
    change('        recognitionRecovery.reset()\n','')
    old = '''                LanaVoiceKeepAliveService.onStopRequested = {
                    if (voiceEnabled) findViewById<Button>(R.id.voiceButton).performClick()
                }
'''
    change(old,'')
    s = s.replace('stopService(Intent(this, LanaVoiceKeepAliveService::class.java))','recognitionBinder?.endSession(recognitionOwner)')
    change('        LanaVoiceKeepAliveService.onStopRequested = null\n','')
    change('        recognizer?.destroy()', '''        recognitionPending = false
        recognitionBinder?.detach(recognitionOwner)
        recognitionBinder = null
        if (recognitionBindingRequested) {
            runCatching { unbindService(recognitionConnection) }
            recognitionBindingRequested = false
        }''')
    change('        restoreConnection()','        restoreConnection()\n        bindRecognitionService()')
    # Startup is retried only after an explicit user start, never from a background callback.
    change('    private fun startVoice() {','    private fun startVoice() {\n        bindRecognitionService()\n        if (!recognitionBindingRequested && recognitionBinder == null) return')
    p.write_text(s)
    workspace = src/'LanaWorkspace.kt'
    ws = workspace.read_text().replace('activity.stopService(android.content.Intent(activity, LanaVoiceKeepAliveService::class.java))','(activity as MainActivity).disableBackgroundRecognition()')
    ws = ws.replace('Экспериментальный фон разговора: уведомление и STOP','Голосовой сервис в фоне: уведомление и STOP · экспериментально')
    workspace.write_text(ws)
