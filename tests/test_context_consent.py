"""Actual local HTTP+SQLite path; fake model transport, zero external API calls."""
import http.client
import json
import tempfile
import threading
import unittest
import uuid
from pathlib import Path
from lana_core.api import create_server
from lana_core.chat import ChatService

class ContextConsentTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.db=Path(self.tmp.name)/'context.db'
        self.calls=[];self.cid=str(uuid.uuid4());self.token='context-test-'+('x'*40)
        def fake(key,model,messages):self.calls.append(messages);return 'Ответ для теста.'
        self.chat=ChatService(self.db,api_key='fake-only',transport=fake)
        from lana_core.engine import TaskQueue
        self.server=create_server(TaskQueue(self.db),self.token,port=0,chat_service=self.chat)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join(timeout=3);self.tmp.cleanup()
    def request(self,**extras):
        body={'conversation_id':self.cid,'text':'Что ты помнишь?',**extras}
        client=http.client.HTTPConnection('127.0.0.1',self.server.server_address[1],timeout=5)
        client.request('POST','/v1/chat',json.dumps(body),{'Authorization':'Bearer '+self.token,'Content-Type':'application/json'})
        response=client.getresponse();status=response.status;data=json.loads(response.read());client.close();return status,data
    def test_default_sends_no_local_notes(self):
        self.assertEqual(self.request()[0],200);self.assertEqual(self.calls[0],[{'role':'user','content':'Что ты помнишь?'}])
    def test_selected_notes_are_user_data_and_not_long_term_memory(self):
        self.assertEqual(self.request(context_notes=['Меня зовут Сандик'],memory_consent=True)[0],200)
        self.assertEqual([x['role'] for x in self.calls[-1]],['user','user'])
        self.assertIn('Сандик',self.calls[-1][0]['content'])
        self.assertEqual(self.calls[-1][-1]['content'],'Что ты помнишь?')
        self.assertEqual(self.chat.memory.all(),[])
        with self.chat._connect() as con:
            self.assertNotIn('Сандик',repr(con.execute('SELECT text FROM chat_messages').fetchall()))
    def test_notes_without_consent_never_reach_model(self):
        for extra in [{'context_notes':['private']},{'context_notes':['private'],'memory_consent':False},{'context_notes':['private'],'memory_consent':'true'},{'context_notes':['private'],'memory_consent':1}]:
            with self.subTest(extra=extra):self.assertEqual(self.request(**extra)[0],400)
        self.assertEqual(self.calls,[])
    def test_bounds_and_unknown_fields_fail_before_model(self):
        for notes in [['x']*6,['x'*3001],['x'*3000,'y'*3000,'z'],[5],{},'text']:
            with self.subTest(notes=notes):self.assertEqual(self.request(context_notes=notes,memory_consent=True)[0],400)
        self.assertEqual(self.request(extra='not allowed')[0],400);self.assertEqual(self.calls,[])
    def test_deselection_stops_new_note_context(self):
        self.request(context_notes=['private selected note'],memory_consent=True)
        self.assertEqual(self.request()[0],200)
        self.assertNotIn('private selected note',repr(self.calls[-1]))
    def test_notes_do_not_bypass_paid_attempt_quota(self):
        self.chat.usage.daily_limit=1
        self.assertEqual(self.request(context_notes=['allowed'],memory_consent=True)[0],200)
        self.assertEqual(self.request(context_notes=['allowed'],memory_consent=True)[0],429)
        self.assertEqual(len(self.calls),1)
    def test_notes_are_not_replayed_automatically_after_server_restart(self):
        self.request(context_notes=['ephemeral context'],memory_consent=True)
        fresh=ChatService(self.db,api_key='fake-only',transport=self.chat.transport)
        fresh.answer(self.cid,'После перезапуска')
        self.assertNotIn('ephemeral context',repr(self.calls[-1]))
