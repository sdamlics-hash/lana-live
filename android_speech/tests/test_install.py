import importlib.util
from pathlib import Path
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('prepare_speech',ROOT/'tools/prepare_existing_speech.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class InstallerTests(unittest.TestCase):
 def test_plan_does_not_write_and_apply_keeps_original(self):
  with tempfile.TemporaryDirectory() as d:
   backend=Path(d)/'lana_api.py';module=Path(d)/'new.py'
   original='from fastapi import FastAPI\napp = FastAPI(\n title="Lana"\n)\n@app.post("/v1/lana/chat")\ndef chat(): return {"reply":"yes"}\n'
   backend.write_text(original);module.write_text('def create_router(): pass\n')
   self.assertEqual('plan',m.prepare(backend,module)['mode']);self.assertEqual(original,backend.read_text())
   result=m.prepare(backend,module,True)
   self.assertEqual(original,Path(result['backup']).read_text())
   self.assertFalse(result['restart_performed']);self.assertIn('@app.post("/v1/lana/chat")',backend.read_text())
   with self.assertRaises(ValueError):m.prepare(backend,module,True)
 def test_ambiguous_app_and_existing_routes_refused(self):
  for source in ['a=FastAPI()\nb=FastAPI()\n','app=FastAPI()\nx="/v1/lana/speech"\n','x=123\n']:
   with self.assertRaises(ValueError):m.planned_source(source)
