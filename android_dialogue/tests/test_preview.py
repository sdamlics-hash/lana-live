import importlib.util
from pathlib import Path
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('v40',ROOT/'android_dialogue/patch_v40.py')
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
class PreviewTests(unittest.TestCase):
    def test_turn_boundaries_and_existing_features_in_generated_app(self):
        with tempfile.TemporaryDirectory() as d:
            out=mod.make_preview(ROOT/'LANA_V3_3_ANDROID_APP_SOURCE.zip',Path(d)/'out')
            src=out/'app/src/main/java/ai/dreibus/lanalive'
            s=(src/'MainActivity.kt').read_text()
            self.assertEqual(s.count('val turn = LanaRecognitionTurn()'),1)
            self.assertEqual(s.count('if (!turn.finish()) return'),2)
            listen=s[s.index('    private fun listenOnce()'):s.index('    private fun retryVoice()')]
            self.assertIn('val recognitionTicket = voiceSession.start()',listen)
            self.assertIn('EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS',listen)
            partial=listen[listen.index('override fun onPartialResults'):listen.index('override fun onEvent')]
            self.assertNotIn('askFromLana',partial);self.assertNotIn('appendHistory',partial)
            self.assertIn('turn.timeout(android.os.SystemClock.elapsedRealtime())',listen)
            self.assertIn('voiceSession.accepts(requestTicket)',s)
            self.assertIn('LanaWorkspace.contextNotes()',s)
            self.assertIn('R.id.connectionStatusText',s)
            self.assertIn('handler.postDelayed',s)
            self.assertNotIn('sk-proj-',s)
            self.assertIn('0.6.0-lana-voice-turns',(out/'app/build.gradle.kts').read_text())
