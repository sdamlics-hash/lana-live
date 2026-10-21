import importlib.util
from pathlib import Path
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('release_preview',ROOT/'android_release/patch_v41.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class ReleasePreviewTests(unittest.TestCase):
    def test_increased_version_and_preserved_features_with_no_default_release_key(self):
        with tempfile.TemporaryDirectory() as d:
            out=m.make_preview(ROOT/'LANA_V3_3_ANDROID_APP_SOURCE.zip',Path(d)/'out')
            build=(out/'app/build.gradle.kts').read_text()
            self.assertIn('versionCode = 6',build)
            self.assertIn('LANA_RELEASE_SIGNING_REQUIRED',build)
            self.assertIn('tasks.configureEach',build)
            self.assertNotIn('debug.keystore',build)
            self.assertNotIn('storePassword = "',build)
            src=out/'app/src/main/java/ai/dreibus/lanalive'
            main=(src/'MainActivity.kt').read_text()
            for anchor in ('LanaRecognitionTurn()','voiceSession.accepts(requestTicket)','LanaWorkspace.contextNotes()','R.id.connectionStatusText'):
                self.assertIn(anchor,main)
