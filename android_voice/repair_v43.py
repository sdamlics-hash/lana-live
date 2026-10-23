"""Voice controls and persistent, secret-free diagnostics atop V4.2."""
import importlib.util
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'
def replace(s, old, new):
    if s.count(old) != 1: raise ValueError('Voice repair anchor missing: ' + old[:70])
    return s.replace(old, new, 1)
def make_preview(archive, destination):
    spec = importlib.util.spec_from_file_location('v42', ROOT/'android_legacy/patch_v42.py')
    base = importlib.util.module_from_spec(spec); spec.loader.exec_module(base)
    out = base.make_preview(archive, destination)
    activity = out/'app/src/main/java/ai/dreibus/lanalive/MainActivity.kt'
    s = activity.read_text()
    s = replace(s, '        tts = TextToSpeech(this)', '        installVoiceControls()\n        tts = TextToSpeech(this)')
    s = replace(s, 'ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA && languageResult != TextToSpeech.LANG_NOT_SUPPORTED', 'ttsReady = languageResult != null && languageResult >= TextToSpeech.LANG_AVAILABLE')
    s = replace(s, '                if (!ttsReady) status.text =', '                refreshVoiceChoices()\n                voiceDiagnostic(if (ttsReady) "Русский синтез речи готов" else "Русский голос недоступен")\n                if (!ttsReady) status.text =')
    # A deliberate button press starts an active conversation. Wake remains available after STOP.
    s = replace(s, '        voiceEnabled = true\n        retryCount = 0\n        listenOnce()', '        voiceEnabled = true\n        conversationActive = true\n        retryCount = 0\n        voiceSession.start()\n        speakFromLana("Да, я здесь! Слушаю тебя.")')
    s = replace(s, '        appendHistory("Лана: " + answer)', '        voiceDiagnostic("Ответ получен; запускаю озвучку")\n        appendHistory("Лана: " + answer)')
    s = replace(s, '        if (!ttsReady) {', '        if (!ttsReady) {\n            voiceDiagnostic("Озвучка недоступна: русский синтез речи не готов")')
    s = replace(s, '        speaking = true\n        status.text = "Отвечаю"', '        speaking = true\n        recognizer?.cancel()\n        applyVoicePreferences()\n        status.text = "Отвечаю"')
    s = replace(s, '            status.text = "Ошибка воспроизведения голоса"', '            voiceDiagnostic("Синтезатор отклонил воспроизведение")\n            status.text = "Ошибка воспроизведения голоса"')
    s = replace(s, '                            speaking = true; status.text = "Отвечаю"', '                            voiceDiagnostic("Озвучка началась")\n                            speaking = true; status.text = "Отвечаю"')
    s = replace(s, '                    override fun onDone(utteranceId: String?) {', '                    override fun onDone(utteranceId: String?) {\n                        handler.post { if (utteranceId == "lana_voice_" + voiceSession.current()) voiceDiagnostic("Озвучка завершена") }')
    s = replace(s, '                                status.text = "Проблема с голосом. Повторяю прослушивание."', '                                voiceDiagnostic("Ошибка синтезатора во время озвучки")\n                                status.text = "Проблема с голосом. Повторяю прослушивание."')
    s = replace(s, '            } else { status.text = "Не удалось запустить синтез речи" }', '            } else { voiceDiagnostic("Ошибка запуска синтеза речи: " + code); status.text = "Не удалось запустить синтез речи" }')
    s = replace(s, '                    when (LanaVoiceCommands.choose(words, conversationActive)) {', '                    val action = LanaVoiceCommands.choose(words, conversationActive)\n                    voiceDiagnostic("Фраза распознана; действие: " + action.name)\n                    when (action) {')
    s = replace(s, '                    } else retryVoice()', '                    } else { voiceDiagnostic("Ошибка распознавания: " + error); retryVoice() }')
    s = replace(s, '        status.text = "Думаю…"', '        voiceDiagnostic("Отправляю вопрос серверу")\n        status.text = "Думаю…"')
    s = replace(s, '            voiceSessionToken = secret', '            voiceSessionToken = secret\n            voiceDiagnostic(if (secret.isBlank()) "Ключ не введён" else "Ключ загружен в текущий сеанс")')
    methods = r'''
    private fun voiceDiagnostic(message: String) {
        val view = findViewById<android.widget.TextView>(R.id.voiceDiagnostics)
        val time = java.text.SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(java.util.Date())
        view.text = (view.text.toString().lines().takeLast(5) + (time + " · " + message)).joinToString("\n")
    }

    private fun installVoiceControls() {
        val prefs = getPreferences(MODE_PRIVATE)
        val rate = findViewById<android.widget.SeekBar>(R.id.voiceRate)
        rate.progress = prefs.getInt("voice_rate", 100).coerceIn(60, 140) - 60
        rate.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(bar: android.widget.SeekBar?) { applyVoicePreferences() }
            override fun onProgressChanged(bar: android.widget.SeekBar?, value: Int, user: Boolean) {
                if (user) prefs.edit().putInt("voice_rate", value + 60).apply()
                findViewById<android.widget.TextView>(R.id.voiceRateLabel).text = "Скорость голоса: " + (value + 60) + "%"
            }
        })
        findViewById<android.widget.TextView>(R.id.voiceRateLabel).text = "Скорость голоса: " + (rate.progress + 60) + "%"
        findViewById<Button>(R.id.testVoice).setOnClickListener {
            if (!ttsReady) { voiceDiagnostic("Русский синтез речи ещё не готов"); return@setOnClickListener }
            voiceEnabled = true
            conversationActive = true
            voiceSession.start()
            recognizer?.cancel()
            speakFromLana("Да, я здесь! Это мой голос. После ответа я снова слушаю тебя.")
        }
    }

    private fun refreshVoiceChoices() {
        val choices = tts?.voices.orEmpty().filter { it.locale.language == "ru" }
            .sortedWith(compareBy<android.speech.tts.Voice> { it.isNetworkConnectionRequired }.thenBy { it.name })
        val names = listOf("Системный русский голос") + choices.map { it.name + if (it.isNetworkConnectionRequired) " (сеть)" else " (на устройстве)" }
        val picker = findViewById<android.widget.Spinner>(R.id.voiceChoice)
        picker.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        val prefs = getPreferences(MODE_PRIVATE)
        val saved = prefs.getString("voice_name", "")
        picker.setSelection((choices.indexOfFirst { it.name == saved } + 1).coerceAtLeast(0))
        picker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putString("voice_name", choices.getOrNull(position - 1)?.name.orEmpty()).apply()
            }
        }
        applyVoicePreferences()
    }

    private fun applyVoicePreferences() {
        val engine = tts ?: return
        val prefs = getPreferences(MODE_PRIVATE)
        val name = prefs.getString("voice_name", "").orEmpty()
        val voice = engine.voices.orEmpty().firstOrNull { it.name == name && it.locale.language == "ru" }
        if (voice != null) engine.setVoice(voice) else engine.setLanguage(Locale("ru", "RU"))
        engine.setSpeechRate(prefs.getInt("voice_rate", 100).coerceIn(60, 140) / 100f)
        engine.setPitch(1.0f)
    }

'''
    s = replace(s, '    private fun appendHistory(line: String) {', methods + '    private fun appendHistory(line: String) {')
    # Timings contain durations only, never recognized text or credentials.
    s = replace(s, '    private var speaking = false',
        '    private var chatRequestStartedAt = 0L\n    private var synthesisRequestedAt = 0L\n    private var speaking = false')
    s = replace(s, '        voiceDiagnostic("Отправляю вопрос серверу")',
        '        chatRequestStartedAt = android.os.SystemClock.elapsedRealtime()\n        voiceDiagnostic("Отправляю вопрос серверу")')
    s = replace(s, '        voiceDiagnostic("Ответ получен; запускаю озвучку")',
        '        if (chatRequestStartedAt > 0L) {\n            voiceDiagnostic("Ожидание сервера, мс: " + (android.os.SystemClock.elapsedRealtime() - chatRequestStartedAt))\n            chatRequestStartedAt = 0L\n        }\n        synthesisRequestedAt = android.os.SystemClock.elapsedRealtime()\n        voiceDiagnostic("Ответ получен; запускаю озвучку")')
    s = replace(s, '                            voiceDiagnostic("Озвучка началась")',
        '                            if (synthesisRequestedAt > 0L) voiceDiagnostic("Запуск синтеза, мс: " + (android.os.SystemClock.elapsedRealtime() - synthesisRequestedAt))\n                            voiceDiagnostic("Озвучка началась")')
    s = replace(s, '                    turn.endedAt?.let { ended ->',
        '                    turn.endedAt?.let { ended ->\n                        voiceDiagnostic("Конец речи → текст, мс: " + (android.os.SystemClock.elapsedRealtime() - ended))')
    activity.write_text(s)
    layout = out/'app/src/main/res/layout/activity_main.xml'
    tree = ET.fromstring(layout.read_text())
    settings = next(n for n in tree.iter() if n.get(A+'id') == '@+id/workspaceSettings')
    def element(tag, id, **attrs):
        return ET.Element(tag, {A+'id':'@+id/'+id, A+'layout_width':'match_parent', A+'layout_height':'wrap_content', **{A+k:str(v) for k,v in attrs.items()}})
    for n in [element('TextView','voiceRateLabel',text='Скорость голоса: 100%',textColor='#342C2A'), element('SeekBar','voiceRate',max=80), element('Spinner','voiceChoice'), element('Button','testVoice',text='ПРОСЛУШАТЬ ГОЛОС')]: settings.append(n)
    # Visible on Conversation, separate from frequently overwritten listening status.
    history_parent = next(n for n in tree.iter() if any(c.get(A+'id') == '@+id/historyText' for c in n))
    history_parent.append(element('TextView','voiceDiagnostics',text='Диагностика голоса',textColor='#342C2A',padding='10dp',textSize='12sp'))
    layout.write_text(ET.tostring(tree, encoding='unicode').replace('LANA Live V4.2', 'LANA Live V4.3'))
    build = out/'app/build.gradle.kts'
    build.write_text(build.read_text().replace('versionCode = 7','versionCode = 8').replace('0.6.2-lana-legacy-contract','0.6.3-lana-voice-repair'))
    return out
if __name__ == '__main__': make_preview(Path(sys.argv[1]), Path(sys.argv[2]))
