"""Build a separate Android V3.5 preview with actual voice → LLM → TTS.

Starts from ORIGINAL V3.3 archive using the independently tested V3.4
patcher. Does not mutate either source archive or an installed application.
"""
import importlib.util
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]


def load_bridge_patcher():
    path = ROOT / "android_bridge" / "patch_v34.py"
    spec = importlib.util.spec_from_file_location("lana_v34_patcher", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def one(text, old, new, label):
    if text.count(old) != 1:
        raise ValueError("V3.4 source changed unexpectedly: " + label)
    return text.replace(old, new, 1)


def make_preview(archive, destination):
    destination = Path(destination)
    bridge = load_bridge_patcher()
    bridge.patch_archive(Path(archive), destination,
                         ROOT / "android_bridge" / "src" / "LanaCoreClient.kt")
    app = destination / "app"
    java = app / "src/main/java/ai/dreibus/lanalive"
    activity = java / "MainActivity.kt"
    main = activity.read_text(encoding="utf-8")

    main = one(main,
               "    private var voiceEnabled = false",
               """    private var voiceEnabled = false
    private var conversationActive = false
    private var voiceSessionToken: String? = null
    private val voiceConversationId: String by lazy {
        val prefs = getPreferences(MODE_PRIVATE)
        val existing = prefs.getString("lana_conversation_id", null)
        if (!existing.isNullOrBlank()) existing else {
            java.util.UUID.randomUUID().toString().also {
                prefs.edit().putString("lana_conversation_id", it).apply()
            }
        }
    }""", "fields")
    main = one(main,
               "            val secret = secretField.text.toString()\n            secretField.text.clear()",
               """            val secret = secretField.text.toString()
            voiceSessionToken = secret
            secretField.text.clear()""", "ephemeral token")
    main = one(main,
               '                status.text = "Слух остановлен"',
               '                conversationActive = false\n                speaking = false\n                status.text = "Слух остановлен"',
               "button stop")

    original_start = '                    if (words.contains("лана", ignoreCase = true)) {'
    original_end = '                    } else retryVoice()'
    start = main.find(original_start)
    if start < 0:
        raise ValueError("V3.3 voice branch not found")
    end = main.find(original_end, start)
    if end < 0:
        raise ValueError("V3.3 voice ending not found")
    end += len(original_end)
    replacement = """                    if (words.contains("лана", ignoreCase = true)) {
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
                    } else retryVoice()"""
    main = main[:start] + replacement + main[end:]
    methods = """    private fun speakFromLana(answer: String) {
        appendHistory("Лана: " + answer)
        if (!ttsReady) {
            status.text = "Русский голос не готов. Ответ: " + answer
            speaking = false
            retryVoice()
            return
        }
        speaking = true
        status.text = "Отвечаю"
        val result = tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "lana_voice_reply")
        if (result != TextToSpeech.SUCCESS) {
            speaking = false
            status.text = "Ошибка воспроизведения голоса"
            retryVoice()
        }
    }

    private fun askFromLana(question: String) {
        val address = findViewById<EditText>(R.id.coreServerUrl).text.toString().trim()
        val secret = voiceSessionToken
        if (secret.isNullOrBlank() || address.isBlank()) {
            speakFromLana("Сначала подключи мой сервер через кнопку проверки связи.")
            return
        }
        speaking = true
        status.text = "Думаю…"
        Thread {
            val reply = try {
                LanaChatClient.ask(address, secret, voiceConversationId, question)
            } catch (_: Exception) {
                "Не получилось связаться с моим ИИ. Попробуй ещё раз."
            }
            runOnUiThread {
                if (voiceEnabled && !isFinishing && !isDestroyed) {
                    speakFromLana(reply)
                } else {
                    speaking = false
                }
            }
        }.start()
    }

"""
    main = one(main, "    private fun appendHistory(line: String) {",
               methods + "    private fun appendHistory(line: String) {",
               "reply helpers")
    activity.write_text(main, encoding="utf-8")
    shutil.copyfile(ROOT / "android_voice/src/LanaChatClient.kt",
                    java / "LanaChatClient.kt")

    gradle = app / "build.gradle.kts"
    text = gradle.read_text(encoding="utf-8")
    text = one(text, 'applicationId = "ai.dreibus.lanalive.bridgepreview"',
               'applicationId = "ai.dreibus.lanalive.voicepreview"', "app id")
    text = one(text, 'versionName = "0.5.0-lana-android-ui"',
               'versionName = "0.5.1-lana-voice-preview"', "version")
    gradle.write_text(text, encoding="utf-8")

    manifest = app / "src/main/AndroidManifest.xml"
    text = manifest.read_text(encoding="utf-8")
    text = one(text, 'android:label="LANA BRIDGE PREVIEW"',
               'android:label="LANA VOICE PREVIEW"', "label")
    manifest.write_text(text, encoding="utf-8")
    return destination


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: python android_voice/patch_v35.py ORIGINAL-V3.3.zip /new/output-dir")
    make_preview(Path(sys.argv[1]), Path(sys.argv[2]))
    print("LANA V3.5 isolated voice preview sources prepared")
