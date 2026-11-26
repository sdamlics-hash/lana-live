from pathlib import Path
import shutil, subprocess, sys
ROOT=Path(__file__).resolve().parents[1]
out=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(ROOT/'android_integrated/prepare_integrated.py'),str(out)],check=True)
src=out/'app/src/main/java/ai/dreibus/lanalive'
tests=out/'app/src/test/java/ai/dreibus/lanalive'
for name in ('LanaOfflineSetupPolicy.kt','LanaOfflineSpeechSetup.kt','LanaUpdater.kt'):
    shutil.copyfile(ROOT/'android_recovery'/name,src/name)
shutil.copyfile(ROOT/'android_recovery/LanaOfflineSetupPolicyTest.kt',tests/'LanaOfflineSetupPolicyTest.kt')
def replace(s,old,new):
    assert s.count(old)==1, 'Unexpected anchor: '+old[:100]
    return s.replace(old,new)
def change(name,old,new):
    p=src/name;p.write_text(replace(p.read_text(),old,new))
manifest=out/'app/src/main/AndroidManifest.xml'
manifest_text=replace(manifest.read_text(),
    '<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />',
    '<uses-permission android:name="android.permission.INTERNET" />')
import re
manifest_text=replace(manifest_text,
    '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />','')
manifest_text, removed = re.subn(
    r'<service\s+android:name="\.ScreenCaptureService"[^>]*/>', '', manifest_text)
assert removed == 1
manifest.write_text(manifest_text)
for file in (src/'LanaUpdateManifest.kt', tests/'LanaUpdateManifestTest.kt'):
    file.write_text(file.read_text().replace('lana-stable-', 'lana-offline-').replace('LANA-STABLE-VOICE', 'LANA-OFFLINE'))

p=src/'MainActivity.kt';s=p.read_text()
# The update permission must not reactivate legacy server checks or screen uploads.
a=s.index('    private val capturePermission =')
b=s.index('    override fun onCreate',a)
s=s[:a]+s[b:]
a=s.index('        val legacyPrefs =')
b=s.index('        historyView =',a)
s=s[:a]+'        for (id in listOf(R.id.legacyServerMode, R.id.coreTestButton, R.id.coreServerUrl,\n            R.id.coreToken, R.id.connectionStatusText, R.id.serverUrl, R.id.startButton)) {\n            findViewById<android.view.View>(id).visibility = android.view.View.GONE\n        }\n'+s[b:]
a=s.index('        serverUrl = findViewById')
b=s.index('    private fun startVoice()',a)
s=s[:a]+'    }\n'+s[b:]
s=replace(s,'        restoreConnection()\n','')
s=replace(s,'    private lateinit var serverUrl: EditText\n','')
s=replace(s,'        // Offline candidate: no network updater.','        lanaUpdater.install(status)')
s=replace(s,'prefs.getString("voice_name","").orEmpty(),prefs.getInt("voice_rate",100)', 'prefs.getString("confirmed_offline_voice","").orEmpty(),prefs.getInt("voice_rate",100)')
# Only show installed, local Russian voices. Selection is not approval.
a=s.index('    private fun refreshVoiceChoices() {');b=s.index('    private fun applyVoicePreferences()',a)
s=s[:a]+'''    private fun refreshVoiceChoices() {
        val choices = tts?.voices.orEmpty().filter { it.locale.language == "ru" && !it.isNetworkConnectionRequired && !it.features.orEmpty().contains("notInstalled") }.sortedBy { it.name }
        val picker = findViewById<android.widget.Spinner>(R.id.voiceChoice)
        val prefs = getPreferences(MODE_PRIVATE)
        val previous = picker.selectedItem as? String
        val names = choices.map { it.name }.distinct()
        val confirmed = prefs.getString("confirmed_offline_voice", "")
        val selection = previous?.takeIf { it in names } ?: confirmed
        val hint = if (names.isEmpty()) "Русские офлайн-голоса не найдены" else "Выбери голос для ответа"
        picker.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf(hint) + names)
        picker.setSelection(names.indexOf(selection) + 1)
        findViewById<Button>(R.id.testVoice).apply {
            text = "Проверить и выбрать голос"
            isEnabled = names.isNotEmpty() && cancelVoicePreview == null
        }
    }

'''+s[b:]
s=replace(s, '    private var tts: TextToSpeech? = null', '''    private var tts: TextToSpeech? = null
    private var cancelVoicePreview: (() -> Unit)? = null''')
a=s.index('        findViewById<Button>(R.id.testVoice).setOnClickListener {',s.index('    private fun installVoiceControls()'))
b=s.index('    private fun refreshVoiceChoices()',a)
s=s[:a]+'''        findViewById<Button>(R.id.testVoice).setOnClickListener {
            if (!memoryTransferAllowed()) { voiceDiagnostic("Сначала останови разговор."); return@setOnClickListener }
            val engine=tts
            val picker = findViewById<android.widget.Spinner>(R.id.voiceChoice)
            val name = (picker.selectedItem as? String).takeIf { picker.selectedItemPosition > 0 }.orEmpty()
            val voice=engine?.voices.orEmpty().firstOrNull { it.name==name && it.locale.language=="ru" && !it.isNetworkConnectionRequired && !it.features.orEmpty().contains("notInstalled") }
            if(engine==null || voice==null || engine.setVoice(voice)!=TextToSpeech.SUCCESS) {
                android.widget.Toast.makeText(this,"Выбери установленный русский офлайн-голос.",android.widget.Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            engine.setSpeechRate(prefs.getInt("voice_rate",100).coerceIn(60,140)/100f)
            engine.setPitch(1f)
            val id = java.util.UUID.randomUUID().toString()
            val button = findViewById<Button>(R.id.testVoice)
            button.isEnabled = false
            lateinit var timeout: Runnable
            val cancel: () -> Unit = {
                cancelVoicePreview = null
                button.removeCallbacks(timeout)
                engine.setOnUtteranceProgressListener(null)
                engine.stop()
                refreshVoiceChoices()
            }
            fun complete(error: String?) {
                if (cancelVoicePreview !== cancel) return
                cancel()
                if (error != null) {
                    voiceDiagnostic(error)
                    android.widget.Toast.makeText(this, error, android.widget.Toast.LENGTH_LONG).show()
                    return
                }
                if (!memoryTransferAllowed()) return
                prefs.edit().putString("confirmed_offline_voice", name).apply()
                voiceDiagnostic("Выбранный голос сохранён для ответов.")
                android.widget.Toast.makeText(this, "Голос для ответов сохранён", android.widget.Toast.LENGTH_SHORT).show()
            }
            timeout = Runnable { complete("Android не завершил пробу голоса вовремя.") }
            cancelVoicePreview = cancel
            val listener = object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) runOnUiThread { complete(null) }
                }
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id) runOnUiThread { complete("Ошибка воспроизведения пробы голоса.") }
                }
            }
            if (engine.setOnUtteranceProgressListener(listener) != TextToSpeech.SUCCESS ||
                engine.speak("Привет! Это проба моего голоса. Как тебе звучание?", TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS
            ) {
                complete("Android не запустил пробу голоса. Проверь выбранный голос в настройках Android.")
                return@setOnClickListener
            }
            button.postDelayed(timeout, 15000)
        }
    }

'''+s[b:]
# Startup must listen before greeting, not append a greeting each time microphone fails.
a=s.index('    private fun startVoice() {');b=s.index('\n    private fun ',a+1)
piece=s[a:b]
assert 'speakFromLana(LanaGreetings.next())' in piece
s=s[:a]+piece.replace('speakFromLana(LanaGreetings.next())','listenOnce()')+s[b:]
s=replace(s,'    override fun onDestroy() {','    override fun onDestroy() {\n        if (::memoryTransfer.isInitialized) memoryTransfer.closeOfflineSetup()')
s=replace(s,'val name = prefs.getString("voice_name", "").orEmpty()', 'val name = prefs.getString("confirmed_offline_voice", "").orEmpty()')
s=replace(s,'    override fun onStop() {', '    override fun onStop() {\n        cancelVoicePreview?.invoke()')
s=replace(s,'    private fun startVoice() {', '    private fun startVoice() {\n        cancelVoicePreview?.invoke()')
s=replace(s,'        super.onResume()', '        super.onResume()\n        refreshVoiceChoices()')
p.write_text(s)
change('LanaRuntimeSpeaker.kt',
    'val selected = local.firstOrNull { it.name == voice.voiceName } ?: local.firstOrNull()',
    """val name = LanaOfflineSetupPolicy.selected(local.map {
                LanaOfflineSetupPolicy.Voice(it.name, it.locale.language, it.isNetworkConnectionRequired,
                    it.features.orEmpty().contains("notInstalled"))
            }, voice.voiceName, engine.voice?.name)
            val selected = local.firstOrNull { it.name == name }""")
change('LanaRuntimeSpeaker.kt',
    'if (selected == null || engine.setVoice(selected) != TextToSpeech.SUCCESS) {\n                finish("Офлайн-голос русского языка не установлен. Ответ доступен текстом."); return\n            }',
    """if (selected == null) {
                finish("Установленный русский офлайн-голос не найден. Ответ доступен текстом."); return
            }
            if (engine.setVoice(selected) != TextToSpeech.SUCCESS) {
                finish("Android не смог включить русский офлайн-голос. Ответ доступен текстом."); return
            }""")
change('LanaMemoryTransfer.kt','    fun mount(panel: LinearLayout) {','''    private val offlineSetup=LanaOfflineSpeechSetup(activity)
    fun closeOfflineSetup(){offlineSetup.close()}
    fun mount(panel: LinearLayout) {
        offlineSetup.mount(panel)''')
change('LanaRecognitionRecovery.kt','Русское распознавание недоступно. Проверь языковой пакет Android или используй текст.','Русское распознавание недоступно. Настройки LANA → Проверить русский офлайн-микрофон. Пока доступен текст.')
# Bound initial model resources correctly if initialize throws.
p=src/'LanaLocalInference.kt';s=p.read_text();s=replace(s,'opened.initialize();engine=opened','engine=opened;opened.initialize()');p.write_text(s)
print('Recovery setup, voice approval, update picker and initialization cleanup integrated.')
