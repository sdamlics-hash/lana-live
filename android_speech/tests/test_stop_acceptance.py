"""Regression: spoken STOP cancels stale responses and does not bill cloud TTS.

Only generates sources from the checked-in V3.3 archive; no device, network,
credentials, provider call, APK install or production deployment.
"""
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PATCH = ROOT / "android_speech" / "patch_v44.py"
ARCHIVE = ROOT / "LANA_V3_3_ANDROID_APP_SOURCE.zip"


class SpokenStopAcceptanceTests(unittest.TestCase):
    def test_stop_invalidates_session_and_uses_only_local_acknowledgement(self):
        spec = importlib.util.spec_from_file_location("lana_v44_stop", PATCH)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            out = module.make_preview(ARCHIVE, Path(directory) / "candidate")
            main = (out / "app/src/main/java/ai/dreibus/lanalive/MainActivity.kt").read_text(
                encoding="utf-8"
            )
            self.assertEqual(main.count("LanaVoiceCommands.Action.STOP -> {"), 1)
            stop = main.split("LanaVoiceCommands.Action.STOP -> {", 1)[1].split(
                "LanaVoiceCommands.Action.IGNORE ->", 1
            )[0]
            self.assertIn("voiceSession.start()", stop)
            self.assertIn("recognitionBinder?.stopConversation(recognitionOwner)", stop)
            self.assertIn("conversationActive = false", stop)
            self.assertIn("stopCloudVoice()", stop)
            self.assertIn("tts?.stop()", stop)
            self.assertIn("tts?.speak(", stop)
            self.assertIn("retryVoice()", stop)
            self.assertNotIn("speakFromLana(", stop)
            self.assertNotIn("LanaSpeechClient.audio(", stop)
            self.assertLess(stop.index("voiceSession.start()"), stop.index("tts?.speak("))
            # Current V4.4 architecture owns GPT/TTS in the foreground service,
            # not in the Activity's old requestTicket callback.
            service = (out / "app/src/main/java/ai/dreibus/lanalive/LanaVoiceKeepAliveService.kt").read_text(
                encoding="utf-8"
            )
            self.assertIn("fun stopConversation(owner: String)", service)
            self.assertIn("conversation.stop()", service)
            self.assertLess(
                stop.index("recognitionBinder?.stopConversation(recognitionOwner)"),
                stop.index("tts?.speak("),
            )

if __name__ == "__main__":
    unittest.main()
