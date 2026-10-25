"""Actual local HTTP diagnostics and route checks, with no external requests."""
import http.client
import json
import sqlite3
import tempfile
import threading
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch
from lana_core.api import create_server
from lana_core.chat import ChatService, ChatError
from lana_core.engine import TaskQueue
TOKEN='test-connections-'+'x'*40
class ConnectionsTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.db=Path(self.temp.name)/'state.db'
        self.calls=[]
        def fake(*args):
            self.calls.append(args)
            return 'Ответ'
        self.chat=ChatService(self.db,api_key='fake-private-key',transport=fake)
        self.server=create_server(TaskQueue(self.db),TOKEN,port=0,chat_service=self.chat)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join(3);self.temp.cleanup()
    def request(self,path,body=None,auth=True):
        c=http.client.HTTPConnection('127.0.0.1',self.server.server_port,timeout=4)
        headers={'Content-Type':'application/json'}
        if auth:headers['Authorization']='Bearer '+TOKEN
        c.request('GET' if body is None else 'POST',path,None if body is None else json.dumps(body),headers)
        r=c.getresponse();result=r.status,json.loads(r.read());c.close();return result
    def test_authenticated_read_only_diagnostics_never_call_model_or_expose_private_data(self):
        self.chat.memory.set('workflow','private','PRIVATE_MEMORY',approved=True)
        self.assertEqual(self.request('/v1/connections',auth=False)[0],401)
        for _ in range(3):
            code,payload=self.request('/v1/connections')
            self.assertEqual(code,200)
            self.assertEqual(payload['schema'],'lana.connections.v1')
            self.assertNotIn('fake-private-key',json.dumps(payload));self.assertNotIn('PRIVATE_MEMORY',json.dumps(payload))
            self.assertEqual(payload['connections']['chatgpt_history']['state'],'not_connected')
        self.assertEqual(self.calls,[])
        self.assertEqual(self.chat.memory.all()[0]['value'],'PRIVATE_MEMORY')
    def test_team_provider_diagnostics_are_explicit_and_never_use_model_calls(self):
        for _ in range(2):
            code, payload = self.request('/v1/connections')
            self.assertEqual(code, 200)
            self.assertEqual(payload['connections']['claude_api']['state'], 'not_configured')
            self.assertEqual(payload['connections']['manus_api']['state'], 'not_configured')
        self.assertEqual(self.calls, [])
        self.assertNotIn('fake-private-key', json.dumps(payload))

    def test_mock_success_never_becomes_real_connected(self):
        self.chat.answer(str(uuid.uuid4()),'Привет')
        self.assertEqual(self.chat.connection_status()['openai_api']['state'],'test_mode')
    def test_key_alone_does_not_prove_connectivity_and_evidence_expires(self):
        # Mock the real transport boundary; no network. This tests state transitions only.
        with patch('lana_core.chat.openai_transport',return_value='Ответ'):
            chat=ChatService(self.db,api_key='fake')
            self.assertEqual(chat.connection_status()['openai_api']['state'],'unverified')
            with patch('lana_core.chat.time.monotonic',return_value=100):
                chat.answer(str(uuid.uuid4()),'Привет')
                self.assertEqual(chat.connection_status()['openai_api']['state'],'verified_recently')
            with patch('lana_core.chat.time.monotonic',return_value=401):
                self.assertEqual(chat.connection_status()['openai_api']['state'],'unverified')
            chat.api_key=''
            self.assertEqual(chat.connection_status()['openai_api']['state'],'not_configured')
    def test_provider_failure_and_memory_failure_are_not_green(self):
        with patch('lana_core.chat.openai_transport',side_effect=ChatError('offline')):
            chat=ChatService(self.db,api_key='fake')
            with self.assertRaises(ChatError):chat.answer(str(uuid.uuid4()),'Привет')
            self.assertEqual(chat.connection_status()['openai_api']['state'],'unavailable')
        with patch.object(self.chat.memory,'_connect',side_effect=sqlite3.OperationalError('private path')):
            result=self.chat.connection_status()
            self.assertEqual(result['lana_memory']['state'],'unavailable')
            self.assertNotIn('private path',json.dumps(result))
    def test_both_chat_paths_share_schema_auth_and_consent(self):
        for path in ('/v1/chat','/v1/lana/chat'):
            data={'conversation_id':str(uuid.uuid4()),'text':'Привет'}
            self.assertEqual(self.request(path,data,auth=False)[0],401)
            self.assertEqual(self.request(path,{**data,'context_notes':['private']})[0],400)
            self.assertEqual(self.request(path,data)[0],200)
        self.assertEqual(len(self.calls),2)
        self.assertEqual(self.request('/v1/lana/chat',{'message':'unknown legacy shape'})[0],400)
