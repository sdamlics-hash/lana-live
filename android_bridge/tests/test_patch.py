import tempfile
import unittest
import zipfile
from pathlib import Path
import importlib.util
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("bridgepatch", ROOT / "patch_v34.py")
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class PatchTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.src = self.root / "v33.zip"
        files = {
            "app/src/main/java/ai/dreibus/lanalive/MainActivity.kt":
                'class MainActivity {\n        status = findViewById(R.id.statusText)\n}',
            "app/src/main/res/layout/activity_main.xml":
                '<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"><LinearLayout>\n    </LinearLayout></ScrollView>',
            "app/build.gradle.kts":
                'applicationId = "ai.dreibus.lanalive"\ncompileSdk = 35',
            "app/src/main/AndroidManifest.xml":
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="LANA LIVE" /></manifest>',
        }
        with zipfile.ZipFile(self.src, "w") as archive:
            for name, value in files.items():
                archive.writestr(name, value)
    def tearDown(self):
        self.temp.cleanup()

    def test_preview_with_separate_application(self):
        out = mod.patch_archive(self.src, self.root / "out", ROOT / "src" / "LanaCoreClient.kt")
        app = out / "app" / "src" / "main"
        self.assertTrue((app / "java/ai/dreibus/lanalive/LanaCoreClient.kt").exists())
        helper = app / "java/ai/dreibus/lanalive/LanaHttpBody.kt"
        native_test = out / "app/src/test/java/ai/dreibus/lanalive/LanaHttpBodyTest.kt"
        self.assertTrue(helper.exists(), "HTTPS size guard must be bundled")
        self.assertTrue(native_test.exists(), "Native Kotlin guard tests must be bundled")
        self.assertIn("readUtf8Bounded", helper.read_text())
        self.assertNotIn("bufferedReader(Charsets.UTF_8)", (app / "java/ai/dreibus/lanalive/LanaCoreClient.kt").read_text())
        activity = (app / "java/ai/dreibus/lanalive/MainActivity.kt").read_text()
        self.assertIn("Thread {", activity)
        self.assertIn("secretField.text.clear()", activity)
        self.assertIn("bridgepreview", (out / "app/build.gradle.kts").read_text())
        self.assertIn("kotlinOptions", (out / "app/build.gradle.kts").read_text())
        layout = app / "res/layout/activity_main.xml"
        ET.parse(layout)
        self.assertIn("coreServerUrl", layout.read_text())
        self.assertIn("LANA BRIDGE PREVIEW", (app / "AndroidManifest.xml").read_text())

    def test_original_zip_not_modified(self):
        data = self.src.read_bytes()
        mod.patch_archive(self.src, self.root / "out", ROOT / "src" / "LanaCoreClient.kt")
        self.assertEqual(self.src.read_bytes(), data)
        with self.assertRaises(ValueError):
            mod.patch_archive(self.src, self.root / "out", ROOT / "src" / "LanaCoreClient.kt")

    def test_refuses_zip_traversal(self):
        evil = self.root / "evil.zip"
        with zipfile.ZipFile(evil, "w") as archive:
            archive.writestr("../evil.txt", "x")
        with self.assertRaises(ValueError):
            mod.patch_archive(evil, self.root / "escape", ROOT / "src" / "LanaCoreClient.kt")

if __name__ == "__main__":
    unittest.main()
