"""Native Shimmer playback, retaining V4.3 local TTS as an explicit setting."""
import importlib.util
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
A='{http://schemas.android.com/apk/res/android}'
def one(s,old,new):
    if s.count(old)!=1:raise ValueError('Voice anchor changed: '+old[:60])
    return s.replace(old,new,1)
def make_preview(archive,destination):
    spec=importlib.util.spec_from_file_location('v43',ROOT/'android_voice/repair_v43.py')
    base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
    out=base.make_preview(archive,destination)
    src=out/'app/src/main/java/ai/dreibus/lanalive';p=src/'MainActivity.kt';s=p.read_text()
    s=one(s,'    private var ttsReady = false','''    private var ttsReady = false
    private var cloudPlayer: android.media.MediaPlayer? = null
    private var cloudClip: java.io.File? = null
    private var cloudAudioGeneration = 0L''')
    s=one(s,'    private fun speakFromLana(answer: String) {','''    private fun speakFromLana(answer: String) {
        if (getPreferences(MODE_PRIVATE).getBoolean("cloud_voice", true)) {
            speakCloudVoice(answer)
            return
        }''')
    # Cloud preview must not depend on Android's optional Russian voice data.
    s=one(s,'            if (!ttsReady) { voiceDiagnostic("Русский синтез речи ещё не готов"); return@setOnClickListener }','''            if (!getPreferences(MODE_PRIVATE).getBoolean("cloud_voice", true) && !ttsReady) {
                voiceDiagnostic("Русский синтез речи ещё не готов"); return@setOnClickListener
            }''')
    s=one(s,'    private fun installVoiceControls() {','''    private fun installVoiceControls() {
        findViewById<android.widget.CheckBox>(R.id.cloudVoice).apply {
            isChecked = getPreferences(MODE_PRIVATE).getBoolean("cloud_voice", true)
            setOnCheckedChangeListener { _, value ->
                stopCloudVoice()
                getPreferences(MODE_PRIVATE).edit().putBoolean("cloud_voice", value).apply()
                voiceDiagnostic(if (value) "Выбран Shimmer · 1,25×" else "Выбран системный голос Android")
            }
        }''')
    s=s.replace('                tts?.stop()','                stopCloudVoice()\n                tts?.stop()')
    s=one(s,'        tts?.shutdown()','        stopCloudVoice()\n        tts?.shutdown()')
    methods=r'''
    private fun stopCloudVoice() {
        cloudAudioGeneration++
        cloudPlayer?.release()
        cloudPlayer = null
        cloudClip?.delete()
        cloudClip = null
        speaking = false
    }

    private fun speakCloudVoice(answer: String) {
        stopCloudVoice()
        appendHistory("Лана: " + answer)
        val address = findViewById<EditText>(R.id.coreServerUrl).text.toString().trim()
        val token = voiceSessionToken.orEmpty()
        if (address.isBlank() || token.isBlank()) {
            voiceDiagnostic("Для Shimmer нужны адрес ядра и ключ текущего сеанса")
            status.text = "Ответ есть; голос не подключён"
            return
        }
        speaking = true
        recognizer?.cancel()
        tts?.stop()
        val ticket = voiceSession.current()
        val audioGeneration = cloudAudioGeneration
        status.text = "Готовлю голос Shimmer…"
        voiceDiagnostic("Запрашиваю Shimmer · 1,25×")
        Thread {
            var result: ByteArray? = null
            var error: String? = null
            try { result = LanaSpeechClient.audio(address, token, answer) }
            catch (e: Exception) {
                error = when (e) {
                    is java.io.IOException, is IllegalArgumentException -> e.message
                    else -> "Не удалось подготовить голос"
                }
            }
            val bytes = result
            val problem = error
            runOnUiThread {
                if (!voiceEnabled || isDestroyed || isFinishing ||
                    !voiceSession.accepts(ticket) || cloudAudioGeneration != audioGeneration) return@runOnUiThread
                if (bytes == null) {
                    speaking = false
                    status.text = "Ответ сохранён; озвучка не получена"
                    voiceDiagnostic(problem ?: "Нет связи с голосом")
                    return@runOnUiThread
                }
                try {
                    val file = java.io.File.createTempFile("lana-speech-", ".mp3", cacheDir)
                    cloudClip = file
                    file.writeBytes(bytes)
                    val player = android.media.MediaPlayer()
                    cloudPlayer = player
                    player.setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    player.setOnPreparedListener {
                        if (cloudPlayer !== player || !voiceEnabled || !voiceSession.accepts(ticket)) return@setOnPreparedListener
                        status.text = "Отвечаю"
                        voiceDiagnostic("Звучит Shimmer · 1,25×")
                        player.start()
                    }
                    player.setOnCompletionListener {
                        if (cloudPlayer !== player || !voiceSession.accepts(ticket)) return@setOnCompletionListener
                        stopCloudVoice()
                        voiceDiagnostic("Shimmer завершил ответ")
                        handler.postDelayed({
                            if (voiceEnabled && !speaking && voiceSession.accepts(ticket)) listenOnce()
                        }, 300)
                    }
                    player.setOnErrorListener { _, _, _ ->
                        if (cloudPlayer === player) {
                            stopCloudVoice()
                            status.text = "Не удалось воспроизвести ответ"
                            voiceDiagnostic("Ошибка воспроизведения Shimmer")
                        }
                        true
                    }
                    player.setDataSource(file.absolutePath)
                    player.prepareAsync()
                } catch (_: Exception) {
                    stopCloudVoice()
                    status.text = "Не удалось воспроизвести ответ"
                    voiceDiagnostic("Ошибка подготовки записи Shimmer")
                }
            }
        }.start()
    }

'''
    s=one(s,'    private fun appendHistory(line: String) {',methods+'    private fun appendHistory(line: String) {');p.write_text(s)
    shutil.copyfile(ROOT/'android_speech/src/LanaSpeechClient.kt',src/'LanaSpeechClient.kt')
    layout=out/'app/src/main/res/layout/activity_main.xml';tree=ET.fromstring(layout.read_text())
    settings=next(n for n in tree.iter() if n.get(A+'id')=='@+id/workspaceSettings')
    settings.insert(0,ET.Element('CheckBox',{A+'id':'@+id/cloudVoice',A+'layout_width':'match_parent',A+'layout_height':'wrap_content',A+'text':'Голос Shimmer · 1,25× (через сервер)',A+'contentDescription':'cloud_voice',A+'textColor':'#342C2A',A+'saveEnabled':'false'}))
    settings.insert(1,ET.Element('TextView',{A+'layout_width':'match_parent',A+'layout_height':'wrap_content',A+'text':'Голос создан ИИ. При выключенном Shimmer используются голос и скорость Android, выбранные ниже.',A+'textColor':'#796258'}))
    layout.write_text(ET.tostring(tree,encoding='unicode').replace('LANA Live V4.3','LANA Live V4.4'))
    build=out/'app/build.gradle.kts';build.write_text(build.read_text().replace('versionCode = 8','versionCode = 9').replace('0.6.3-lana-voice-repair','0.6.4-lana-shimmer'))
    return out
if __name__=='__main__':make_preview(Path(sys.argv[1]),Path(sys.argv[2]))
