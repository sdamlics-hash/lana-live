import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"LANA_V3_3_ANDROID_APP_SOURCE.zip"
PATCH=ROOT/"android_voice/patch_v36.py"

spec=importlib.util.spec_from_file_location("voice_v36",PATCH)
v36=importlib.util.module_from_spec(spec)
spec.loader.exec_module(v36)

class V36PreviewTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.out=Path(self.temp.name)/"lana-v36"

    def tearDown(self):
        self.temp.cleanup()

    def test_real_zip_generates_reliable_voice_preview(self):
        target=v36.make_preview(SRC,self.out)
        main=(target/"app/src/main/java/ai/dreibus/lanalive/MainActivity.kt").read_text()
        self.assertIn("LanaVoiceCommands.choose(words, conversationActive)",main)
        self.assertIn("LanaVoiceCommands.Action.STOP",main)
        self.assertIn('conversationActive = false',main)
        self.assertIn("handler.removeCallbacksAndMessages(null)",main)
        self.assertIn('status.text = if (conversationActive) "Слушаю"',main)
        self.assertIn("handler.post {\n                            speaking = false",main)
        self.assertNotIn("words.equals(\"Лана, хватит\", ignoreCase = true)",main)
        app=target/"app"
        self.assertTrue((app/"src/main/java/ai/dreibus/lanalive/LanaVoiceCommands.kt").is_file())
        self.assertTrue((app/"src/test/java/ai/dreibus/lanalive/LanaVoiceCommandsTest.kt").is_file())
        gradle=(app/"build.gradle.kts").read_text()
        self.assertIn("versionName = \"0.5.2-lana-voice-reliable\"",gradle)
        self.assertIn('testImplementation("junit:junit:4.13.2")',gradle)

    def test_original_archive_unchanged(self):
        before=SRC.read_bytes()
        v36.make_preview(SRC,self.out)
        self.assertEqual(before,SRC.read_bytes())

    def test_no_lingering_crash_if_directory_preexists(self):
        v36.make_preview(SRC,self.out)
        with self.assertRaises(ValueError):
            v36.make_preview(SRC,self.out)

if __name__=="__main__":
    unittest.main()
