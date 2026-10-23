from pathlib import Path

def install(out: Path, root: Path):
    src = out/'app/src/main/java/ai/dreibus/lanalive'
    tests = out/'app/src/test/java/ai/dreibus/lanalive'
    for name, folder in [('LanaRecognitionRecovery.kt', src), ('LanaRecognitionRecoveryTest.kt', tests)]:
        (folder/name).write_text((root/'android_team'/('src' if folder == src else 'tests')/name).read_text())
    p = src/'MainActivity.kt'
    s = p.read_text()
    def change(old, new):
        nonlocal s
        if s.count(old) != 1: raise ValueError('Expected unique resilience anchor: '+old[:90])
        s = s.replace(old,new)
    change('    private fun listenOnce() {', '''    private val recognitionRecovery = LanaRecognitionRecovery()
    private var scheduledRecovery: Runnable? = null
    private fun scheduleRecognitionRecovery(delayMs: Long) {
        scheduledRecovery?.let { handler.removeCallbacks(it) }
        val ticket = voiceSession.current()
        val pending = Runnable {
            scheduledRecovery = null
            if (voiceSession.accepts(ticket) && voiceEnabled && !speaking) listenOnce()
        }
        scheduledRecovery = pending
        handler.postDelayed(pending, delayMs)
    }
    private fun recoverRecognition(code: Int) {
        if (!voiceEnabled) return
        val decision = recognitionRecovery.error(code)
        if (decision.diagnostic) voiceDiagnostic(decision.message)
        status.text = decision.message
        if (decision.stop) {
            voiceSession.stop()
            voiceEnabled = false
            cancelRecognition()
            stopService(Intent(this, LanaVoiceKeepAliveService::class.java))
            findViewById<Button>(R.id.voiceButton).text = "Слушать"
        } else {
            if (decision.recreate) { recognizer?.destroy(); recognizer = null }
            scheduleRecognitionRecovery(decision.delayMs)
        }
    }
    private fun listenOnce() {''')
    change('        retryCount = 0\n        voiceSession.start()', '        retryCount = 0\n        recognitionRecovery.reset()\n        voiceSession.start()')
    change('        val turn = LanaRecognitionTurn()\n        activeRecognitionTurn = turn', '''        val turn = LanaRecognitionTurn()
        var ready = false
        activeRecognitionTurn = turn
        handler.postDelayed({
            if (voiceSession.accepts(recognitionTicket) && voiceEnabled && !ready && turn.finish()) {
                cancelRecognition()
                recognizer?.destroy(); recognizer = null
                recoverRecognition(11)
            }
        }, LanaRecognitionRecovery.START_TIMEOUT_MS)
        handler.postDelayed({
            if (voiceSession.accepts(recognitionTicket) && voiceEnabled && turn.finish()) {
                cancelRecognition()
                voiceDiagnostic("Служба задержала завершение фразы; переподключаю микрофон")
                recoverRecognition(11)
            }
        }, LanaRecognitionRecovery.TURN_TIMEOUT_MS)''')
    change('                    status.text = if (conversationActive) "Слушаю" else "Слушаю: скажи «Лана, ты тут?»"\n                }\n                override fun onBeginningOfSpeech', '                    ready = true\n                    status.text = if (conversationActive) "Слушаю" else "Слушаю: скажи «Лана, ты тут?»"\n                }\n                override fun onBeginningOfSpeech')
    start = s.index('                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)')
    end = s.index('\n                }\n                override fun onResults', start)
    s = s[:start] + '                    recoverRecognition(error)' + s[end:]
    change('                    retryCount = 0\n                    val action', '                    retryCount = 0\n                    recognitionRecovery.reset()\n                    val action')
    change('        handler.postDelayed({ if (voiceSession.accepts(retryTicket) && voiceEnabled && !speaking) listenOnce() }, delay)', '        scheduleRecognitionRecovery(delay)')
    # The delayed generation check in scheduleRecognitionRecovery rejects STOP and destroyed sessions.
    change('        val retryTicket = voiceSession.current()\n        scheduleRecognitionRecovery(delay)', '        scheduleRecognitionRecovery(delay)')
    change('        try { recognizer?.startListening(intent) } catch (_: Exception) {\n            if (turn.finish()) { status.text = "Ошибка запуска микрофона"; retryVoice() }\n        }', '''        try { recognizer?.startListening(intent) } catch (_: Exception) {
            if (turn.finish()) recoverRecognition(3)
        }''')
    p.write_text(s)
