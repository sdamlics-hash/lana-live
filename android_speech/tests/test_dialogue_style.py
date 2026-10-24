import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class DialogueStyleIntegration(unittest.TestCase):
    def test_generated_visible_and_background_dialogue_use_the_same_persisted_style(self):
        spec = importlib.util.spec_from_file_location('style_preview', ROOT/'android_speech/patch_v44.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            out = module.make_preview(ROOT/'LANA_V3_3_ANDROID_APP_SOURCE.zip',Path(directory)/'app')
            src = out/'app/src/main/java/ai/dreibus/lanalive'
            self.assertIn('LanaDialogueSettings.prompt(this,question)',(src/'MainActivity.kt').read_text())
            self.assertIn('LanaDialogueSettings.prompt(this@LanaVoiceKeepAliveService,text)',(src/'LanaVoiceKeepAliveService.kt').read_text())
            self.assertIn('LanaDialogueSettings.mount(activity,settings)',(src/'LanaWorkspace.kt').read_text())
            self.assertTrue((src/'LanaDialogueStyle.kt').exists())
            self.assertTrue((out/'app/src/test/java/ai/dreibus/lanalive/LanaDialogueStyleTest.kt').exists())
