import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('workspace_patch',ROOT/'android_workspace/patch_v37.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class WorkspaceGenerationTests(unittest.TestCase):
    def test_existing_ids_voice_guard_and_archive_preserved(self):
        archive=ROOT/'LANA_V3_3_ANDROID_APP_SOURCE.zip';before=archive.read_bytes()
        with tempfile.TemporaryDirectory() as d:
            out=module.make_preview(archive,Path(d)/'out')
            root=ET.parse(out/'app/src/main/res/layout/activity_main.xml').getroot()
            a='{http://schemas.android.com/apk/res/android}'
            ids=[node.get(a+'id') for node in root.iter() if node.get(a+'id')]
            self.assertEqual(len(ids),len(set(ids)))
            for name in ['statusText','voiceButton','historyText','serverUrl','startButton','coreServerUrl','coreToken','coreTestButton','workspaceSettings']:
                self.assertIn('@+id/'+name,ids)
            main=(out/'app/src/main/java/ai/dreibus/lanalive/MainActivity.kt').read_text()
            self.assertIn('LanaWorkspace(this).mount()',main)
            self.assertIn('voiceSession.accepts(requestTicket)',main)
            self.assertIn('LanaChatClient.ask(',main)
            self.assertTrue((out/'app/src/main/java/ai/dreibus/lanalive/LanaWorkspace.kt').is_file())
            self.assertEqual(archive.read_bytes(),before)
            with self.assertRaises(ValueError):module.make_preview(archive,Path(d)/'out')
