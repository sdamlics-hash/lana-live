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
p=src/'MainActivity.kt';s=p.read_text()
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
        val hint = if (names.isEmpty()) "Русские офлайн-голоса не найдены" else "Выбери и прослушай женский голос"
        picker.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf(hint) + names)
        picker.setSelection(names.indexOf(selection) + 1)
        findViewById<Button>(R.id.testVoice).isEnabled = names.isNotEmpty()
    }

'''+s[b:]
a=s.index('        findViewById<Button>(R.id.testVoice).setOnClickListener {',s.index('    private fun installVoiceControls()'))
b=s.index('    private fun refreshVoiceChoices()',a)
s=s[:a]+'''        findViewById<Button>(R.id.testVoice).setOnClickListener {
            if (!memoryTransferAllowed()) { voiceDiagnostic("Сначала останови разговор."); return@setOnClickListener }
            val engine=tts
            val picker = findViewById<android.widget.Spinner>(R.id.voiceChoice)
            val name = (picker.selectedItem as? String).takeIf { picker.selectedItemPosition > 0 }.orEmpty()
            val voice=engine?.voices.orEmpty().firstOrNull { it.name==name && it.locale.language=="ru" && !it.isNetworkConnectionRequired && !it.features.orEmpty().contains("notInstalled") }
            if(engine==null || voice==null || engine.setVoice(voice)!=TextToSpeech.SUCCESS) {
                android.widget.Toast.makeText(this,"Выбери установленный русский офлайн-голос. При необходимости загрузи женский голос в настройках Android.",android.widget.Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            engine.setSpeechRate(prefs.getInt("voice_rate",100).coerceIn(60,140)/100f)
            engine.setPitch(1f)
            if(engine.speak("Привет! Это проба моего голоса. Как тебе звучание?",TextToSpeech.QUEUE_FLUSH,null,"lana_local_voice_preview")!=TextToSpeech.SUCCESS) {
                voiceDiagnostic("Android не запустил пробу голоса. Проверь выбранный голос в настройках Android.")
                return@setOnClickListener
            }
            android.app.AlertDialog.Builder(this).setTitle("Подходит этот женский голос?")
                .setMessage("Подтверди только после прослушивания. Другой голос Лана сама не подставит.")
                .setNegativeButton("Нет") { _,_ -> engine.stop() }
                .setPositiveButton("Да, сохранить") { _,_ -> engine.stop();prefs.edit().putString("confirmed_offline_voice",name).commit() }.show()
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
s=replace(s,'        super.onResume()', '        super.onResume()\n        refreshVoiceChoices()')
p.write_text(s)
change('LanaRuntimeSpeaker.kt','val selected = local.firstOrNull { it.name == voice.voiceName } ?: local.firstOrNull()','val selected = local.firstOrNull { it.name == voice.voiceName && voice.voiceName.isNotBlank() }')
change('LanaRuntimeSpeaker.kt','Офлайн-голос русского языка не установлен. Ответ доступен текстом.','Выбери и подтверди женский офлайн-голос в Настройках. Ответ доступен текстом.')
change('LanaMemoryTransfer.kt','    fun mount(panel: LinearLayout) {','''    private val offlineSetup=LanaOfflineSpeechSetup(activity)
    fun closeOfflineSetup(){offlineSetup.close()}
    fun mount(panel: LinearLayout) {
        offlineSetup.mount(panel)''')
change('LanaRecognitionRecovery.kt','Русское распознавание недоступно. Проверь языковой пакет Android или используй текст.','Русское распознавание недоступно. Настройки LANA → Проверить русский офлайн-микрофон. Пока доступен текст.')
# Bound initial model resources correctly if initialize throws.
p=src/'LanaLocalInference.kt';s=p.read_text();s=replace(s,'opened.initialize();engine=opened','engine=opened;opened.initialize()');p.write_text(s)
print('Recovery setup, voice approval, update picker and initialization cleanup integrated.')
