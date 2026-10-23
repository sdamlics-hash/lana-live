from pathlib import Path

def install(out: Path, root: Path):
    src = out/'app/src/main/java/ai/dreibus/lanalive'
    (src/'LanaHandsFreeGate.kt').write_text((root/'android_team/src/LanaHandsFreeGate.kt').read_text())
    (out/'app/src/test/java/ai/dreibus/lanalive/LanaHandsFreeGateTest.kt').write_text((root/'android_team/tests/LanaHandsFreeGateTest.kt').read_text())
    (src/'LanaSessionTrace.kt').write_text((root/'android_team/src/LanaSessionTrace.kt').read_text())
    manifest = out/'app/src/main/AndroidManifest.xml'
    manifest.write_text(manifest.read_text().replace('<application', '<uses-permission android:name="android.permission.WAKE_LOCK" /><application',1))
    p = src/'MainActivity.kt'
    s = p.read_text()
    s = s.replace('private var recognitionPending = false', 'private var recognitionPending = false\n    private var screenDetached = false')
    s = s.replace('if (!voiceEnabled || speaking) return', 'if (screenDetached || !voiceEnabled || speaking) return')
    s = s.replace('if (isFinishing || isDestroyed) return@observer', 'if (screenDetached || isFinishing || isDestroyed) return@observer')
    s = s.replace('recognitionBinder?.observeConversation(recognitionOwner,conversationObserver)', 'recognitionBinder?.observeConversation(recognitionOwner,voiceSession.current(),conversationObserver)')
    s = s.replace('binder.observeConversation(recognitionOwner,conversationObserver)', 'binder.observeConversation(recognitionOwner,voiceSession.current(),conversationObserver)')
    old = '        binder.listen(recognitionOwner,ticket,recognitionObserver)'
    assert s.count(old) == 1
    s = s.replace(old,'''        val address = findViewById<EditText>(R.id.coreServerUrl).text.toString().trim()
        binder.configureHandsFree(recognitionOwner,LanaRuntimeRequest(address,
            if (voiceSessionTokenAddress == address) voiceSessionToken.orEmpty() else "",
            voiceConversationId,"","",findViewById<android.widget.CheckBox>(R.id.legacyServerMode).isChecked,
            LanaWorkspace.contextNotes(this@MainActivity)),runtimeVoice(),conversationActive)
''' + old)
    old = '    override fun onDestroy() {'
    assert s.count(old) == 1
    s = s.replace(old,'''    override fun onStop() {
        screenDetached = true
        pendingRuntimeAction = null
        recognitionPending = false
        if (voiceEnabled) recognitionBinder?.detach(recognitionOwner)
        super.onStop()
    }

''' + old)
    old = '        if (::lanaUpdater.isInitialized) lanaUpdater.resume()'
    assert s.count(old) == 1
    s = s.replace(old, old + '''
        if (screenDetached && voiceEnabled) {
            screenDetached = false
            if (recognitionBinder?.sessionEnded() == true) {
                recognitionObserver.stopped("Голосовой сеанс остановлен. Нажми «Слушать» для нового сеанса.")
                return
            }
            recognitionBinder?.attach(recognitionOwner,recognitionObserver)
            recognitionBinder?.observeConversation(recognitionOwner,voiceSession.current(),conversationObserver)
            if (recognitionBinder?.conversationBusy() != true) { speaking = false; listenOnce() }
        } else screenDetached = false
''')
    # Configuration also travels with the first answer, before any recognition turn has completed.
    config = s[s.index('        val address = findViewById<EditText>(R.id.coreServerUrl)',s.index('private fun listenOnce()')):s.index('        binder.listen(recognitionOwner,ticket,recognitionObserver)')]
    old = '            action(binder)'
    assert s.count(old) == 1
    s = s.replace(old,config + old)
    old = '            recognitionBinder?.attach(recognitionOwner,recognitionObserver)'
    # Recover a service-owned cycle after Activity recreation without silently turning it off.
    first = s.index(old,s.index('override fun onServiceConnected'))
    s = s[:first] + s[first:].replace(old,'''            if (recognitionBinder?.attach(recognitionOwner,recognitionObserver) == true) {
                voiceEnabled = true; conversationActive = true; voiceSession.start(); recognitionPending = true
            }''',1)
    old = '            val pending = pendingRuntimeAction; pendingRuntimeAction = null'
    assert s.count(old) == 1
    s = s.replace(old, '            recognitionBinder?.let { binder ->\n' + config + '            }\n' + old)
    s = s.replace('recognitionBinder?.report() ?: "Голосовой сервис не подключён"',
        'recognitionBinder?.report() ?: ("Голосовой сервис не подключён\\n" + getSharedPreferences("lana_session_trace",MODE_PRIVATE).getString("events", "Нет событий"))')
    p.write_text(s)
    test = out/'app/src/test/java/ai/dreibus/lanalive/LanaDeviceClockTest.kt'
    t = test.read_text().replace('class LanaDeviceClockTest {', '''class LanaDeviceClockTest {
    @Test fun samsungCompoundDateAndLocationUsesClockWithoutInventingLocation() {
        val reply = LanaDeviceClock.answer("Лана какое сегодня число и где мы находимся",Date.from(Instant.parse("2026-10-23T20:00:00Z")),TimeZone.getTimeZone("Europe/Berlin"))!!
        assertTrue(reply.contains("23 октября 2026"))
        assertTrue(reply.contains("Местоположение не определено"))
        assertNull(LanaDeviceClock.answer("Какая погода и где мы находимся"))
    }
''')
    test.write_text(t)
    p = out/'app/src/main/java/ai/dreibus/lanalive/LanaWorkspace.kt'
    s = p.read_text().replace('fun contextNotes(activity: Activity)', 'fun contextNotes(activity: android.content.Context)')
    p.write_text(s)
