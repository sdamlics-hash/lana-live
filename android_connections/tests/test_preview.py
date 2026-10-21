import importlib.util
from pathlib import Path
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('v39',ROOT/'android_connections/patch_v39.py')
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
class PreviewTests(unittest.TestCase):
    def test_generated_app_preserves_voice_memory_and_has_status_client(self):
        with tempfile.TemporaryDirectory() as d:
            out=mod.make_preview(ROOT/'LANA_V3_3_ANDROID_APP_SOURCE.zip',Path(d)/'out')
            src=out/'app/src/main/java/ai/dreibus/lanalive'
            main=(src/'MainActivity.kt').read_text()
            self.assertIn('voiceSession.accepts(requestTicket)',main)
            self.assertIn('val chosenNotes = LanaWorkspace.contextNotes()',main)
            self.assertIn('connectionStatusText',main)
            self.assertIn('LanaConnectionStatus.chatUrl(baseUrl)',(src/'LanaChatClient.kt').read_text())
            self.assertIn('/v1/connections',(src/'LanaCoreClient.kt').read_text())
            self.assertIn('0.5.5-lana-connections',(out/'app/build.gradle.kts').read_text())
