import importlib.util
from pathlib import Path
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('legacy',ROOT/'android_legacy/patch_v42.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class LegacyPreviewTests(unittest.TestCase):
    def test_legacy_scope_and_release_guard_preserve_existing_app(self):
        with tempfile.TemporaryDirectory() as d:
            out=m.make_preview(ROOT/'LANA_V3_3_ANDROID_APP_SOURCE.zip',Path(d)/'out')
            s=(out/'app/src/main/java/ai/dreibus/lanalive/MainActivity.kt').read_text()
            self.assertLess(s.index('LanaLegacyQuota.claim(this)'),s.index('var serverSucceeded = false'))
            self.assertLess(s.index('if (!voiceSession.accepts(requestTicket)) return@runOnUiThread'),s.index('legacyDialogHistory.commit'))
            self.assertIn('legacyMode, priorTurns)',s)
            self.assertIn('LanaWorkspace.contextNotes()',s)
            self.assertIn('LanaRecognitionTurn()',s)
            self.assertIn('connectionStatusText',s)
            src = out/'app/src/main/java/ai/dreibus/lanalive'
            client = (src/'LanaChatClient.kt').read_text()
            self.assertIn('LanaHttpBody.readUtf8Bounded',client)
            self.assertIn('errorStream?.use',client)
            self.assertTrue((src/'LanaHttpBody.kt').exists())
            build=(out/'app/build.gradle.kts').read_text()
            self.assertIn('versionCode = 7',build)
            self.assertIn('LANA_RELEASE_SIGNING_REQUIRED',build)
