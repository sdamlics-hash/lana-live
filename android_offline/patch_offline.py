"""Generate a deliberately offline candidate from the unchanged 1048 pipeline.
Only the generated tree changes. No publishing or signing here.
"""
from pathlib import Path
import re, shutil, subprocess, sys
ROOT = Path(__file__).resolve().parents[1]

def one(s, old, new):
    if s.count(old) != 1:
        raise ValueError("Offline patch anchor must be unique: "+old[:120])
    return s.replace(old,new)

def cut(s, start, end, replacement):
    a=s.index(start); b=s.index(end,a)
    return s[:a]+replacement+s[b:]

def install(out):
    src=out/'app/src/main/java/ai/dreibus/lanalive'
    test=out/'app/src/test/java/ai/dreibus/lanalive'
    shutil.copyfile(ROOT/'android_offline/LanaOfflineCore.kt',src/'LanaOfflineCore.kt')
    shutil.copyfile(ROOT/'android_offline/LanaOfflineCoreTest.kt',test/'LanaOfflineCoreTest.kt')
    # All ordinary foreground and text requests use the offline core after existing local intents.
    f=src/'MainActivity.kt'; s=f.read_text()
    s=cut(s,'        val modelQuestion = try {','\n    private fun voiceDiagnostic',
        '        speakFromLana(LanaOfflineCore().answer(question))\n    }\n\n')
    s=one(s,'return LanaRuntimeVoice(!typedTextOnly,prefs.getBoolean("cloud_voice",true),address,token,',
        'return LanaRuntimeVoice(!typedTextOnly,false,address,token,')
    s=one(s,'val capability = LanaTeamVoice.localReply(question)',
        'val capability = LanaOfflineCore.capability(question,LanaTeamVoice.localReply(question))')
    # Team panel and voice dispatcher must not make a paid request.
    a=s.index('    internal fun askTeamTask(prompt: String, complete: (String?, String?) -> Unit) {')
    b=s.index('\n    private fun appendHistory(',a)
    s=s[:a]+'''    internal fun askTeamTask(prompt: String, complete: (String?, String?) -> Unit) {
        complete(null,LanaOfflineCore.TEAM_DISABLED)
    }

'''+s[b:]
    # No automatic release check or update control in this experiment.
    s=one(s,'        lanaUpdater.install(status)','        // Offline candidate: no network updater.')
    # Existing connection settings are not erased. Hide only the cloud voice toggle.
    s=one(s,'findViewById<android.widget.CheckBox>(R.id.cloudVoice).apply {',
        'findViewById<android.widget.CheckBox>(R.id.cloudVoice).apply {\n            visibility = android.view.View.GONE')
    # Initial typed input remains available if local STT is unavailable.
    f.write_text(s)
    # Detached/background conversation takes the same local route. Existing local workspace and STOP remain.
    f=src/'LanaVoiceKeepAliveService.kt'; s=f.read_text()
    s=one(s,'LanaTeamVoice.localReply(text)?.let { say(it); return }',
          'LanaOfflineCore.capability(text,LanaTeamVoice.localReply(text))?.let { say(it); return }')
    s=cut(s,'                val task = try {','\n            }\n        })',
        '''                if (LanaTeamVoice.task(text) != null) {
                    say(LanaOfflineCore.TEAM_DISABLED); return
                }
                if (LanaCoordinator.handles(text) || LanaTeamVoice.wantsLastReply(text)) {
                    say("Для изменения записей команды открой её экран. Ничего не отправлено."); return
                }
                say(LanaOfflineCore().answer(text))''')
    s=one(s,'LanaRuntimeVoice(voice.enabled,voice.cloud,voice.address,voiceToken,',
        'LanaRuntimeVoice(voice.enabled,false,voice.address,voiceToken,')
    f.write_text(s)
    # Defense in depth: service transport cannot call any model endpoint.
    f=src/'LanaRuntimeTransport.kt'; s=f.read_text()
    s=cut(s,'    override fun ask(request: LanaRuntimeRequest,','    /** Local-only memory lookup.',
        '''    override fun ask(request: LanaRuntimeRequest, complete: (String?, String?) -> Unit): LanaConversationRuntime.Cancel {
        complete(null,LanaOfflineCore.TEAM_DISABLED)
        return LanaConversationRuntime.Cancel {}
    }
''')
    f.write_text(s)
    # Prefer actual on-device recognizer rather than the advisory EXTRA_PREFER_OFFLINE alone.
    f=src/'LanaServiceRecognizer.kt'; s=f.read_text()
    s=one(s,'            if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(context)',
        '''            if (android.os.Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                error(13,"local_model_unavailable"); return
            }
            if (recognizer == null) recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)''')
    s=one(s,'                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU")',
        '                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU")\n                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)')
    f.write_text(s)
    # Never invoke cloud TTS, even if an old saved preference is true.
    f=src/'LanaRuntimeSpeaker.kt'; s=f.read_text()
    s=one(s,'        if (voice.cloud) cloud(id,spoken,voice) else system(id,spoken,voice)',
        '        system(id,spoken,voice)')
    s=one(s,'''            val selected = engine.voices.orEmpty().firstOrNull { it.name == voice.voiceName && it.locale.language == "ru" }
            if (selected != null) engine.voice = selected else engine.setLanguage(Locale("ru","RU"))''',
        '''            val local = engine.voices.orEmpty().filter {
                it.locale.language == "ru" && !it.isNetworkConnectionRequired && !it.features.orEmpty().contains("notInstalled")
            }
            val selected = local.firstOrNull { it.name == voice.voiceName } ?: local.firstOrNull()
            if (selected == null || engine.setVoice(selected) != TextToSpeech.SUCCESS) {
                finish("Офлайн-голос русского языка не установлен. Ответ доступен текстом."); return
            }''')
    # Remove unreachable cloud output implementation rather than retaining it in the offline speaker.
    s=cut(s,'    private fun cloud(id: Long,','    fun close()','')
    f.write_text(s)
    # Disallow network access at the Android permission boundary, including merged library declarations.
    f=out/'app/src/main/AndroidManifest.xml'; s=f.read_text()
    if 'xmlns:tools=' not in s:
        s=one(s,'<manifest','<manifest xmlns:tools="http://schemas.android.com/tools"')
    s=re.sub(r'<uses-permission\b[^>]*android:name\s*=\s*["\x27]android\.permission\.INTERNET["\x27][^>]*/\s*>','',s)
    s=one(s,'<application','<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />\n    <application')
    f.write_text(s)
    print("Offline candidate patched. APK and device verification still required.")

if __name__=='__main__':
    out=Path(sys.argv[1]).resolve()
    subprocess.run([sys.executable,str(ROOT/'android_speech/patch_v44.py'),str(ROOT/'LANA_V3_3_ANDROID_APP_SOURCE.zip'),str(out)],check=True)
    install(out)
