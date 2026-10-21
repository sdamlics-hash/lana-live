"""V4.0: bounded recognition turns and explicit endpointer hints, atop V3.9."""
import importlib.util
from pathlib import Path
import shutil
import sys
ROOT=Path(__file__).resolve().parents[1]
def exact(s,old,new,label):
    if s.count(old)!=1:raise ValueError('Unexpected V3.9 anchor: '+label)
    return s.replace(old,new,1)
def make_preview(archive,destination):
    spec=importlib.util.spec_from_file_location('v39',ROOT/'android_connections/patch_v39.py')
    base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
    out=base.make_preview(archive,Path(destination))
    src=out/'app/src/main/java/ai/dreibus/lanalive'
    activity=src/'MainActivity.kt';s=activity.read_text()
    begin=s.index('        voiceEnabled = true\n',s.index('    private fun startVoice()'))
    end=s.index('        listenOnce()\n    }',begin)
    setup=s[begin:end]
    setup=exact(setup,'        voiceEnabled = true\n','', 'move enabled flag')
    setup=exact(setup,'\n        retryCount = 0\n','\n', 'move retry reset')
    setup=exact(setup,'        val recognitionTicket = voiceSession.start()',
        '        val recognitionTicket = voiceSession.start()\n        val turn = LanaRecognitionTurn()', 'new turn')
    s=s[:begin]+'        voiceEnabled = true\n        retryCount = 0\n'+s[end:]
    setup=exact(setup,'override fun onBeginningOfSpeech() {}', '''override fun onBeginningOfSpeech() {
                    if (!voiceSession.accepts(recognitionTicket) || !turn.acceptsEvents()) return
                    turn.speechBegan()
                    status.text = if (conversationActive) "Слушаю" else "Слушаю: скажи «Лана, ты тут?»"
                }''','speech resumed')
    setup=exact(setup,'override fun onEndOfSpeech() {}','''override fun onEndOfSpeech() {
                    if (!voiceSession.accepts(recognitionTicket) || !turn.acceptsEvents()) return
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (!turn.endOfSpeech(now)) return
                    status.text = "Распознаю фразу…"
                    handler.postDelayed({
                        if (!voiceSession.accepts(recognitionTicket) || !voiceEnabled) return@postDelayed
                        if (turn.timeout(android.os.SystemClock.elapsedRealtime())) {
                            recognizer?.cancel()
                            status.text = "Распознавание задержалось. Слушаю снова."
                            retryVoice()
                        }
                    }, LanaRecognitionTurn.FINAL_TIMEOUT_MS)
                }''','bounded final wait')
    setup=exact(setup,'override fun onPartialResults(partialResults: Bundle?) {}','''override fun onPartialResults(partialResults: Bundle?) {
                    if (!voiceSession.accepts(recognitionTicket) || !turn.acceptsEvents()) return
                    // Preview only. Partial hypotheses never become paid requests/history.
                    val preview = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (preview.isNotBlank()) status.text = "Слушаю: " + preview.take(160)
                }''','partial preview')
    ready = 'override fun onReadyForSpeech(params: Bundle?) {\n                    if (!voiceSession.accepts(recognitionTicket)) return'
    setup=exact(setup,ready,ready.replace('if (!voiceSession.accepts(recognitionTicket))', 'if (!voiceSession.accepts(recognitionTicket) || !turn.acceptsEvents())'),'ready callback')
    for signature in ('override fun onResults(results: Bundle?) {','override fun onError(error: Int) {'):
        anchor=signature+'\n                    if (!voiceSession.accepts(recognitionTicket)) return'
        setup=exact(setup,anchor,anchor+'\n                    if (!turn.finish()) return','single terminal callback')
    setup=exact(setup,'                    val words = results?', '''                    turn.endedAt?.let { ended ->
                        android.util.Log.i("LanaVoiceTiming", "speech_end_to_final_ms=" + (android.os.SystemClock.elapsedRealtime() - ended))
                    }
                    val words = results?''','no-content timing')
    anchor='        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {'
    s=exact(s,anchor,setup+anchor,'fresh recognizer per turn')
    s=exact(s,'putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)', '''putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // These are recognizer hints, not a guaranteed cross-device VAD.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, LanaRecognitionTurn.TARGET_SILENCE_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, LanaRecognitionTurn.INCOMPLETE_SILENCE_MS)''','silence hints')
    s=exact(s,'try { recognizer?.startListening(intent) } catch (_: Exception) { status.text = "Ошибка запуска микрофона" }', '''try { recognizer?.startListening(intent) } catch (_: Exception) {
            if (turn.finish()) { status.text = "Ошибка запуска микрофона"; retryVoice() }
        }''','start failure recovery')
    activity.write_text(s)
    shutil.copyfile(ROOT/'android_dialogue/src/LanaRecognitionTurn.kt',src/'LanaRecognitionTurn.kt')
    tests=out/'app/src/test/java/ai/dreibus/lanalive'
    shutil.copyfile(ROOT/'android_dialogue/tests/LanaRecognitionTurnTest.kt',tests/'LanaRecognitionTurnTest.kt')
    build=out/'app/build.gradle.kts';build.write_text(build.read_text().replace('0.5.5-lana-connections','0.6.0-lana-voice-turns'))
    layout=out/'app/src/main/res/layout/activity_main.xml';layout.write_text(layout.read_text().replace('LANA Live V3.9','LANA Live V4.0'))
    return out
if __name__=='__main__':
    make_preview(Path(sys.argv[1]),Path(sys.argv[2]))
