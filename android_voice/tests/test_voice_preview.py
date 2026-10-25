import tempfile
import unittest
import zipfile
from pathlib import Path
import importlib.util

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "LANA_V3_3_ANDROID_APP_SOURCE.zip"
PATCH = ROOT / "android_voice" / "patch_v35.py"

spec = importlib.util.spec_from_file_location("voicepatch", PATCH)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class VoicePreviewTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.out = Path(self.temp.name) / "preview"

    def tearDown(self):
        self.temp.cleanup()

    def test_original_v33_archive_patches_to_v35(self):
        self.assertTrue(SRC.is_file(), "Original V3.3 ZIP missing from repo")
        path = mod.make_preview(SRC, self.out)
        kotlin = (path / "app/src/main/java/ai/dreibus/lanalive/MainActivity.kt").read_text()
        self.assertIn("LanaChatClient.ask(", kotlin)
        self.assertIn("conversationActive = true", kotlin)
        self.assertIn("speakFromLana", kotlin)
        self.assertIn("voiceSessionToken = secret", kotlin)
        self.assertIn("tts?.speak(", kotlin)
        self.assertIn("handler.postDelayed({ if (voiceEnabled) listenOnce() }", kotlin)
        gradle = (path / "app/build.gradle.kts").read_text()
        self.assertIn('applicationId = "ai.dreibus.lanalive.voicepreview"', gradle)
        self.assertTrue((path / "app/src/main/java/ai/dreibus/lanalive/LanaChatClient.kt").exists())
        helper = path / "app/src/main/java/ai/dreibus/lanalive/LanaHttpBody.kt"
        self.assertTrue(helper.exists(), "Voice build must include bounded HTTPS decoder")
        self.assertIn("LanaHttpBody.readUtf8Bounded", (path / "app/src/main/java/ai/dreibus/lanalive/LanaChatClient.kt").read_text())

    def test_original_archive_unchanged(self):
        before=SRC.read_bytes()
        mod.make_preview(SRC, self.out)
        self.assertEqual(SRC.read_bytes(), before)
        with self.assertRaises(ValueError):
            mod.make_preview(SRC, self.out)

    def test_client_has_no_hardcoded_api_secret(self):
        client=(ROOT / "android_voice/src/LanaChatClient.kt").read_text()
        self.assertIn('HttpsURLConnection', client)
        self.assertIn('"/v1/chat"', client)
        self.assertIn("Bearer $bearerToken", client)
        self.assertNotIn("sk-proj-",client)
        self.assertNotIn("trustAll",client)

if __name__ == "__main__":
    unittest.main()
