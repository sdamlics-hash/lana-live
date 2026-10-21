"""Make an isolated LANA voice V3.6 build without touching the installed APK.

Extends the validated V3.5 generator and fixes Russian wake/stop routing
and stale voice callbacks. This patch does not deploy any server.
"""
import importlib.util
from pathlib import Path
import shutil
import sys

ROOT=Path(__file__).resolve().parents[1]

def load_base():
    source=ROOT / "android_voice" / "patch_v35.py"
    spec=importlib.util.spec_from_file_location("lana_v35_base", source)
    mod=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod

def exact(value, original, replacement, purpose):
    if value.count(original)!=1:
        raise ValueError(f"Unexpected V3.5 source at {purpose}: {value.count(original)} occurrences")
    return value.replace(original,replacement,1)

def make_preview(original_zip, destination):
    v35=load_base()
    result=v35.make_preview(original_zip,Path(destination))
    app=result / "app"
    src=app / "src/main/java/ai/dreibus/lanalive"
    main_path=src / "MainActivity.kt"
    main=main_path.read_text(encoding="utf-8")

    old='''                    if (words.contains("лана", ignoreCase = true)) {
                        conversationActive = true
                        if (words.contains("ты тут", ignoreCase = true) && words.length < 55) {
                            speakFromLana("Да, я здесь! Слушаю тебя.")
                        } else {
                            askFromLana(words)
                        }
                    } else if (conversationActive && words.isNotBlank()) {
                        if (words.equals("Лана, хватит", ignoreCase = true) ||
                            words.equals("стоп разговор", ignoreCase = true)) {
                            conversationActive = false
                            speakFromLana("Хорошо. Позови, когда понадоблюсь.")
                        } else {
                            askFromLana(words)
                        }
                    } else retryVoice()'''
    new='''                    when (LanaVoiceCommands.choose(words, conversationActive)) {
                        LanaVoiceCommands.Action.WAKE -> {
                            conversationActive = true
                            speakFromLana("Да, я здесь! Слушаю тебя.")
                        }
                        LanaVoiceCommands.Action.ASK -> {
                            conversationActive = true
                            askFromLana(words)
                        }
                        LanaVoiceCommands.Action.STOP -> {
                            conversationActive = false
                            speakFromLana("Хорошо. Позови, когда понадоблюсь.")
                        }
                        LanaVoiceCommands.Action.IGNORE -> retryVoice()
                    }'''
    main=exact(main,old,new,"voice command classifier")

    # Android TTS listener callbacks may arrive from a non-main thread.
    # Only update UI / mutable Activity state on the main looper.
    main=exact(main,
       '''                    override fun onStart(utteranceId: String?) { speaking = true }''',
       '''                    override fun onStart(utteranceId: String?) {
                        handler.post { speaking = true; status.text = "Отвечаю" }
                    }''',"tts start")
    main=exact(main,
       '''                    override fun onDone(utteranceId: String?) {
                        speaking = false
                        handler.postDelayed({ if (voiceEnabled) listenOnce() }, 500)
                    }''',
       '''                    override fun onDone(utteranceId: String?) {
                        handler.post {
                            speaking = false
                            if (voiceEnabled) {
                                handler.postDelayed({
                                    if (voiceEnabled && !speaking) listenOnce()
                                }, 500)
                            }
                        }
                    }''',"tts complete")
    main=exact(main,
       '''                    override fun onError(utteranceId: String?) {
                        speaking = false
                        handler.postDelayed({ if (voiceEnabled) listenOnce() }, 800)
                    }''',
       '''                    override fun onError(utteranceId: String?) {
                        handler.post {
                            speaking = false
                            if (voiceEnabled) {
                                status.text = "Проблема с голосом. Повторяю прослушивание."
                                handler.postDelayed({
                                    if (voiceEnabled && !speaking) listenOnce()
                                }, 800)
                            }
                        }
                    }''',"tts error")

    main=exact(main,
       '''                voiceEnabled = false
                recognizer?.cancel()
                tts?.stop()''',
       '''                voiceEnabled = false
                handler.removeCallbacksAndMessages(null)
                recognizer?.cancel()
                tts?.stop()''',"voice stop")
    main=exact(main,
       '''override fun onReadyForSpeech(params: Bundle?) { status.text = "Слушаю: скажи «Лана, ты тут?»" }''',
       '''override fun onReadyForSpeech(params: Bundle?) {
                    status.text = if (conversationActive) "Слушаю" else "Слушаю: скажи «Лана, ты тут?»"
                }''',"listening status")
    main_path.write_text(main,encoding="utf-8")
    shutil.copyfile(ROOT / "android_voice/src/LanaVoiceCommands.kt",src / "LanaVoiceCommands.kt")

    # A native JVM test for actual Kotlin command classification, not just
    # static Python text searches or a mock implementation.
    test_dir=app / "src/test/java/ai/dreibus/lanalive"
    test_dir.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(ROOT / "android_voice/tests/LanaVoiceCommandsTest.kt",
                    test_dir / "LanaVoiceCommandsTest.kt")
    build=app / "build.gradle.kts"
    gradle=build.read_text(encoding="utf-8")
    gradle=exact(gradle,
                 'versionName = "0.5.1-lana-voice-preview"',
                 'versionName = "0.5.2-lana-voice-reliable"',"version")
    gradle+='\\n// Offline Kotlin tests do not use the OpenAI API.\\ndependencies { testImplementation("junit:junit:4.13.2") }\\n'
    build.write_text(gradle,encoding="utf-8")
    return result

if __name__=="__main__":
    if len(sys.argv)!=3:
        raise SystemExit("Usage: python android_voice/patch_v36.py V3.3.zip /new/source")
    make_preview(Path(sys.argv[1]),Path(sys.argv[2]))
    print("Prepared isolated LANA V3.6 voice-reliability sources")
