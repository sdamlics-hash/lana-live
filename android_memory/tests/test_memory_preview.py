import importlib.util
from pathlib import Path
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('memory_patch',ROOT/'android_memory/patch_v38.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class MemoryPreviewTests(unittest.TestCase):
    def test_context_snapshot_stays_inside_guarded_voice_request(self):
        with tempfile.TemporaryDirectory() as d:
            out=module.make_preview(ROOT/'LANA_V3_3_ANDROID_APP_SOURCE.zip',Path(d)/'out')
            folder=out/'app/src/main/java/ai/dreibus/lanalive'
            main=(folder/'MainActivity.kt').read_text()
            self.assertIn('val chosenNotes = LanaWorkspace.contextNotes()',main)
            self.assertIn('voiceSession.accepts(requestTicket)',main)
            self.assertIn('question, chosenNotes)',main)
            client=(folder/'LanaChatClient.kt').read_text()
            self.assertIn('memory_consent',client);self.assertIn('contextNotes.isNotEmpty()',client)
            self.assertNotIn('sk-proj-',client)
            workspace=(folder/'LanaWorkspace.kt').read_text()
            self.assertIn('setOnCheckedChangeListener',workspace)
            self.assertIn('selectedNotes.remove',workspace)
            self.assertNotIn('putString("selectedNotes"',workspace)
