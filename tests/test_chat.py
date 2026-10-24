import http.client
import json
import tempfile
import threading
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch
from lana_core.chat import ChatService, ChatError, MAX_HISTORY_MESSAGES, PERSONA, openai_transport
from lana_core.api import create_server
from lana_core.engine import TaskQueue

TOKEN = "integration-test-credentials-" + "a"*40

class ChatTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.db = Path(self.temp.name) / "lana.db"
        self.calls = []
        def fake(key, model, messages):
            self.calls.append(messages)
            return "Привет! Ответ " + str(len(messages))
        self.chat = ChatService(self.db, api_key="mock-only-key", transport=fake)
        self.cid = str(uuid.uuid4())

    def tearDown(self):
        self.temp.cleanup()

    def test_russian_chat_and_short_memory(self):
        self.assertIn("Ответ", self.chat.answer(self.cid, "Лана, ты тут?"))
        self.assertEqual(len(self.calls[0]), 1)
        self.chat.answer(self.cid, "Как дела?")
        self.assertEqual(len(self.calls[1]), 3)
        with self.chat._connect() as con:
            rows=con.execute("SELECT role,text FROM chat_messages ORDER BY id").fetchall()
        self.assertEqual([row[0] for row in rows], ["user","assistant","user","assistant"])

    def test_memory_persists_across_restart(self):
        self.chat.answer(self.cid, "Первый вопрос")
        other = ChatService(self.db, api_key="mock-key", transport=self.chat.transport)
        other.answer(self.cid, "Продолжаем")
        self.assertEqual(self.calls[-1][0]["content"], "Первый вопрос")

    def test_no_cross_conversation_leak_and_history_bound(self):
        for x in range(12):
            self.chat.answer(self.cid, f"Вопрос {x}")
        self.chat.answer(str(uuid.uuid4()), "Новый диалог")
        self.assertEqual(len(self.calls[-1]), 1)
        with self.chat._connect() as con:
            count = con.execute("SELECT COUNT(*) FROM chat_messages WHERE conversation_id=?", (self.cid,)).fetchone()[0]
        self.assertEqual(count, MAX_HISTORY_MESSAGES)

    def test_fail_without_real_key_or_bad_text(self):
        other = ChatService(self.db, api_key="", transport=self.chat.transport)
        with self.assertRaises(ChatError):
            other.answer(self.cid, "Привет")
        for value in ("", "x"*3000, 5):
            with self.assertRaises(ValueError):
                self.chat.answer(self.cid, value)
        with self.assertRaises(ValueError):
            self.chat.answer("not a UUID", "Привет")

    def test_error_does_not_store_turn(self):
        def fail(*args):
            raise ChatError("unavailable")
        failing = ChatService(self.db, api_key="mock", transport=fail)
        with self.assertRaises(ChatError):
            failing.answer(self.cid, "Проверка")
        with failing._connect() as con:
            self.assertEqual(con.execute("SELECT COUNT(*) FROM chat_messages").fetchone()[0], 0)

    def test_response_api_payload_privacy_and_parsing(self):
        sent = {}
        class FakeResponse:
            def __enter__(self): return self
            def __exit__(self,*args): pass
            def read(self,*args): return b""
            def __iter__(self): return iter(())
        def urlopen(req, timeout):
            sent.update(json.loads(req.data))
            return FakeResponse2()
        class FakeResponse2:
            def __enter__(self): return self
            def __exit__(self,*args): pass
            def read(self, size=-1): return '{"output":[{"content":[{"type":"output_text","text":"Да, слушаю!"}]}]}'.encode("utf-8")
        with patch("lana_core.chat._open_openai_request", urlopen):
            answer = openai_transport("fake-key","gpt-4.1-mini",[{"role":"user","content":"Привет"}])
        self.assertEqual(answer, "Да, слушаю!")
        self.assertFalse(sent["store"])
        self.assertEqual(sent["model"], "gpt-4.1-mini")
        self.assertIn("Лана", PERSONA)

    def test_chat_api_authorization(self):
        queue = TaskQueue(self.db)
        server = create_server(queue,TOKEN,port=0,chat_service=self.chat)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        def call(auth, data):
            conn=http.client.HTTPConnection("127.0.0.1",server.server_address[1],timeout=4)
            headers={"Content-Type":"application/json"}
            if auth: headers["Authorization"]="Bearer "+TOKEN
            conn.request("POST","/v1/chat",json.dumps(data).encode(),headers)
            response=conn.getresponse()
            result=(response.status,json.loads(response.read()))
            conn.close()
            return result
        try:
            payload={"conversation_id":self.cid,"text":"Лана, привет"}
            self.assertEqual(call(False,payload)[0],401)
            code,result=call(True,payload)
            self.assertEqual(code,200)
            self.assertIn("Ответ",result["answer"])
            self.assertEqual(call(True,{"text":"нет id"})[0],400)
        finally:
            server.shutdown();server.server_close();thread.join(3)

if __name__ == "__main__":
    unittest.main()
