import importlib.util
from pathlib import Path
import tempfile
import unittest
from fastapi import FastAPI
from fastapi.testclient import TestClient
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('speech_router',ROOT/'android_speech/server/lana_speech_router.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
TOKEN='test-token-for-speech-'+'x'*32
class SpeechTests(unittest.TestCase):
    def setUp(self):
        self.dir=tempfile.TemporaryDirectory();self.addCleanup(self.dir.cleanup)
        self.calls=[]
        def transport(key,text):self.calls.append((key,text));return b'ID3-test-audio'
        app=FastAPI();app.include_router(m.create_router(TOKEN,'fake-test-key',Path(self.dir.name)/'quota.sqlite',transport))
        self.client=TestClient(app);self.headers={'Authorization':'Bearer '+TOKEN}
    def test_unauthorized_cannot_call_provider(self):
        self.assertEqual(401,self.client.post('/v1/lana/speech',json={'text':'Привет'}).status_code)
        self.assertEqual([],self.calls)
    def test_status_calls_no_provider_and_does_not_claim_verified(self):
        result=self.client.get('/v1/lana/speech/status',headers=self.headers)
        self.assertFalse(result.json()['provider_verified']);self.assertEqual([],self.calls)
    def test_one_request_audio_and_no_private_headers(self):
        r=self.client.post('/v1/lana/speech',json={'text':'Привет'},headers=self.headers)
        self.assertEqual(200,r.status_code);self.assertEqual(b'ID3-test-audio',r.content)
        self.assertEqual('no-store',r.headers['cache-control']);self.assertEqual(1,len(self.calls))
    def test_empty_oversize_extra_fields_refused_without_provider(self):
        for payload in [{'text':' '},{'text':'x'*4097},{'text':'Hi','voice':'other'}]:
            self.assertIn(self.client.post('/v1/lana/speech',json=payload,headers=self.headers).status_code,[400,422])
        self.assertEqual([],self.calls)
    def test_persistent_hourly_limit(self):
        path=Path(self.dir.name)/'limit.sqlite';q=m.SpeechQuota(path)
        for i in range(30):self.assertTrue(q.claim(100000))
        self.assertFalse(m.SpeechQuota(path).claim(100001))
        self.assertTrue(m.SpeechQuota(path).claim(104000))
    def test_provider_failure_does_not_echo_key_or_text(self):
        def fail(key,text):raise RuntimeError(key+text)
        app=FastAPI();app.include_router(m.create_router(TOKEN,'secret-provider-key',Path(self.dir.name)/'failure.sqlite',fail))
        r=TestClient(app).post('/v1/lana/speech',json={'text':'private question'},headers=self.headers)
        self.assertEqual(502,r.status_code);self.assertNotIn('secret',r.text);self.assertNotIn('private question',r.text)
    def test_native_integration_generates_with_cloud_guard_and_preserves_identity(self):
        spec=importlib.util.spec_from_file_location('v44',ROOT/'android_speech/patch_v44.py')
        mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
        out=mod.make_preview(ROOT/'LANA_V3_3_ANDROID_APP_SOURCE.zip',Path(self.dir.name)/'app')
        s=(out/'app/src/main/java/ai/dreibus/lanalive/MainActivity.kt').read_text()
        self.assertIn('cloudAudioGeneration != audioGeneration',s)
        self.assertIn('voiceSession.accepts(ticket)',s)
        self.assertIn('cloudClip?.delete()',s)
        self.assertIn('На сервере ещё не подключён голос Shimmer',(out/'app/src/main/java/ai/dreibus/lanalive/LanaSpeechClient.kt').read_text())
        build=(out/'app/build.gradle.kts').read_text()
        self.assertIn('ai.dreibus.lanalive.voicepreview',build)
        self.assertIn('LANA_RELEASE_SIGNING_REQUIRED',build)
