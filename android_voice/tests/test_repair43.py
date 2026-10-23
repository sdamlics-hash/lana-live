import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('repair43', ROOT/'android_voice/repair_v43.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class VoiceRepairIntegration(unittest.TestCase):
    def test_integrated_generator_preserves_contract_and_exposes_voice_controls(self):
        with tempfile.TemporaryDirectory() as d:
            out=m.make_preview(ROOT/'LANA_V3_3_ANDROID_APP_SOURCE.zip',Path(d)/'app')
            source=(out/'app/src/main/java/ai/dreibus/lanalive/MainActivity.kt').read_text()
            start=source.split('private fun startVoice()',1)[1].split('private fun speakFromLana',1)[0]
            self.assertIn('conversationActive = true',start)
            self.assertIn('speakFromLana("Да, я здесь! Слушаю тебя.")',start)
            self.assertIn('legacyMode, priorTurns)',source)
            self.assertIn('if (!voiceSession.accepts(requestTicket))',source)
            self.assertIn('voiceDiagnostic("Ошибка распознавания: " + error)',source)
            self.assertIn('languageResult != null && languageResult >= TextToSpeech.LANG_AVAILABLE',source)
            tree=ET.fromstring((out/'app/src/main/res/layout/activity_main.xml').read_text())
            ids=[n.get(m.A+'id') for n in tree.iter()]
            for id in ['voiceChoice','voiceRate','testVoice','voiceDiagnostics','workspaceSettings','historyText']:
                self.assertEqual(1,ids.count('@+id/'+id))
            build=(out/'app/build.gradle.kts').read_text()
            self.assertIn('versionCode = 8',build)
            self.assertIn('LANA_RELEASE_SIGNING_REQUIRED',build)
