from pathlib import Path

def install(out: Path, root: Path):
    src = out/'app/src/main/java/ai/dreibus/lanalive'
    tests = out/'app/src/test/java/ai/dreibus/lanalive'
    for name in ('LanaConversationRuntime.kt','LanaRuntimeTransport.kt','LanaRuntimeSpeaker.kt','LanaVoiceKeepAliveService.kt'):
        (src/name).write_text((root/'android_team/src'/name).read_text())
    (tests/'LanaConversationRuntimeTest.kt').write_text((root/'android_team/tests/LanaConversationRuntimeTest.kt').read_text())
    p = src/'MainActivity.kt'; s = p.read_text()
    def change(old,new):
        nonlocal s
        if s.count(old) != 1: raise ValueError('Conversation runtime anchor not unique: '+old[:80])
        s = s.replace(old,new)
    # Keep local intent handling and role/clock prompts; delegate the actual dialogue request.
    a = s.index('        val address = findViewById<EditText>(R.id.coreServerUrl).text.toString().trim()',s.index('        val modelQuestion = try { LanaDialoguePrompt.build(question) }'))
    b = s.index('\n    private fun voiceDiagnostic',a)
    s = s[:a]+'''        val address = findViewById<EditText>(R.id.coreServerUrl).text.toString().trim()
        val token = voiceSessionToken
        if (token.isNullOrBlank() || address.isBlank() || voiceSessionTokenAddress != address) {
            speakFromLana("Сначала подключи мой сервер через кнопку проверки связи."); return
        }
        val request = LanaRuntimeRequest(address,token,voiceConversationId,question,modelQuestion,
            findViewById<android.widget.CheckBox>(R.id.legacyServerMode).isChecked,LanaWorkspace.contextNotes(this))
        val voice = runtimeVoice()
        speaking = true; cancelRecognition()
        status.text = "Думаю…"
        withConversationRuntime { it.ask(recognitionOwner,request,voice,voiceSession.current()) }
    }

''' + s[b:]
    a = s.index('    private fun speakFromLana(answer: String) {')
    b = s.index('    private fun askFromLana(question: String)',a)
    s = s[:a]+'''    private fun runtimeVoice(): LanaRuntimeVoice {
        val prefs = getPreferences(MODE_PRIVATE)
        val address = findViewById<EditText>(R.id.coreServerUrl).text.toString().trim()
        val token = if (voiceSessionTokenAddress == address) voiceSessionToken.orEmpty() else ""
        return LanaRuntimeVoice(!typedTextOnly,prefs.getBoolean("cloud_voice",true),address,token,
            prefs.getString("voice_name","").orEmpty(),prefs.getInt("voice_rate",100).coerceIn(60,140)/100f,localClassName)
    }
    private var pendingRuntimeAction: ((LanaVoiceKeepAliveService.LocalBinder) -> Unit)? = null
    private var renderedRuntimeTurn = -1L
    private val conversationObserver: (LanaConversationRuntime.Snapshot) -> Unit = observer@ { snapshot ->
        if (isFinishing || isDestroyed) return@observer
        if (snapshot.answer.isNotBlank() && renderedRuntimeTurn != snapshot.turn) {
            renderedRuntimeTurn = snapshot.turn
            history.clear()
            val saved = getPreferences(MODE_PRIVATE).getString("voice_history","").orEmpty()
            history.addAll(saved.lines().filter { it.isNotBlank() }.takeLast(30))
            if (!saved.endsWith("Лана: " + snapshot.answer)) history.add("Лана: " + snapshot.answer)
            renderHistory()
        }
        if (!voiceEnabled || !voiceSession.accepts(snapshot.clientTicket)) return@observer
        if (!snapshot.contextSaved) voiceDiagnostic("Не удалось сохранить часть контекста или истории. Восстановление после закрытия не подтверждено.")
        when (snapshot.phase) {
            LanaConversationRuntime.Phase.THINKING -> { speaking = true; status.text = "Думаю…" }
            LanaConversationRuntime.Phase.PREPARING_VOICE -> { speaking = true; status.text = "Готовлю голос…" }
            LanaConversationRuntime.Phase.SPEAKING -> { speaking = true; status.text = "Отвечаю" }
            LanaConversationRuntime.Phase.READY -> {
                speaking = false; chatRequestStartedAt = 0L
                if (snapshot.textOnly) { typedTextOnly = false; voiceEnabled = false; status.text = "Ответ текстом получен" }
                else {
                    status.text = "Ответ завершён"
                    val ticket = voiceSession.current()
                    handler.postDelayed({ if (voiceEnabled && !speaking && voiceSession.accepts(ticket)) listenOnce() },300)
                }
            }
            LanaConversationRuntime.Phase.ERROR -> {
                speaking = false; status.text = snapshot.error ?: "Ответ не подтверждён"
                voiceDiagnostic(status.text.toString())
                if (snapshot.textOnly) { typedTextOnly = false; voiceEnabled = false } else retryVoice()
            }
            LanaConversationRuntime.Phase.STOPPED -> { speaking = false }
            LanaConversationRuntime.Phase.IDLE -> Unit
        }
    }
    private fun withConversationRuntime(action: (LanaVoiceKeepAliveService.LocalBinder) -> Unit) {
        val binder = recognitionBinder
        if (binder != null) {
            binder.attach(recognitionOwner,recognitionObserver)
            binder.observeConversation(recognitionOwner,conversationObserver)
            action(binder)
        } else {
            val ticket = voiceSession.current()
            pendingRuntimeAction = { connected -> if (voiceEnabled && voiceSession.accepts(ticket)) action(connected) }
            bindRecognitionService()
            handler.postDelayed({
                if (pendingRuntimeAction != null && voiceEnabled && voiceSession.accepts(ticket)) {
                    pendingRuntimeAction = null; speaking = false
                    status.text = "Голосовой модуль не подключился. Запрос не отправлен."
                }
            },12000)
        }
    }
    private fun speakFromLana(answer: String) {
        speaking = true; cancelRecognition()
        val voice = runtimeVoice()
        withConversationRuntime { it.say(recognitionOwner,answer,voice,voiceSession.current()) }
    }

''' + s[b:]
    a=s.index('    private fun stopCloudVoice() {'); b=s.index('    private fun restoreConnection()',a)
    s=s[:a]+'''    private fun stopCloudVoice() {
        pendingRuntimeAction = null
        recognitionBinder?.stopConversation(recognitionOwner)
        speaking = false
        if (voiceEnabled && !isFinishing && !isDestroyed) retryVoice()
    }

''' + s[b:]
    change('            recognitionBinder?.attach(recognitionOwner,recognitionObserver)\n            if (recognitionPending', '''            recognitionBinder?.attach(recognitionOwner,recognitionObserver)
            recognitionBinder?.observeConversation(recognitionOwner,conversationObserver)
            val pending = pendingRuntimeAction; pendingRuntimeAction = null
            recognitionBinder?.let { pending?.invoke(it) }
            if (recognitionPending''')
    # Output cancellation belongs to the Service. Destroying a screen only detaches it; a foreground in-flight turn may finish.
    start = s.index('    override fun onDestroy() {')
    tail = s[start:]
    tail = tail.replace('        recognitionBinder?.endSession(recognitionOwner)\n','').replace('        stopCloudVoice()\n','')
    tail = tail.replace('        recognitionPending = false','        pendingRuntimeAction = null\n        recognitionPending = false')
    s = s[:start]+tail
    change('    private fun startVoice() {\n        bindRecognitionService()', '''    private fun startVoice() {
        if (recognitionBinder?.conversationBusy() == true) {
            status.text = "Предыдущий ответ ещё готовится. Дождись завершения или нажми STOP в уведомлении."
            return
        }
        bindRecognitionService()''')
    a = s.index('        tts = TextToSpeech(this) { code ->')
    b = s.index('        findViewById<Button>(R.id.voiceButton).setOnClickListener',a)
    s = s[:a]+'''        tts = TextToSpeech(this) { code ->
            if (code == TextToSpeech.SUCCESS) {
                val language = tts?.setLanguage(Locale("ru","RU"))
                ttsReady = language != null && language >= TextToSpeech.LANG_AVAILABLE
                refreshVoiceChoices()
                voiceDiagnostic(if (ttsReady) "Список системных голосов готов" else "Русский системный голос недоступен; облачная озвучка настраивается отдельно")
            } else voiceDiagnostic("Список системных голосов не подготовлен")
        }
'''+s[b:]
    # The remaining Activity TTS is a voice-settings enumerator; it never speaks the dialogue.
    s=s.replace('    private var cloudPlayer: android.media.MediaPlayer? = null\n','').replace('    private var cloudClip: java.io.File? = null\n','').replace('    private var cloudAudioGeneration = 0L\n','')
    change('internal fun memoryTransferAllowed(): Boolean = !voiceEnabled && !speaking && try {', 'internal fun memoryTransferAllowed(): Boolean = !voiceEnabled && !speaking && recognitionBinder?.conversationBusy() != true && try {')
    p.write_text(s)
