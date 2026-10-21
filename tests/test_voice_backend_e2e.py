"""End-to-end simulated LANA dialogue through the *real* local HTTP bridge.

These tests use no Android microphone, no external network, no OpenAI quota,
no real secrets, and no access to production Gmail or trading robots.
"""
import http.client
import json
import sqlite3
import tempfile
import threading
import unittest
import uuid
from pathlib import Path
from lana_core.api import create_server
from lana_core.chat import ChatService, ChatError
from lana_core.engine import TaskQueue

TOKEN = "local-integration-test-" + "K8m3N6q9P2w5E7r0T4y1U8i3O5p6A9s2"


class LocalVoiceBackendIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db = str(Path(self.tmp.name) / "lana_simulated_voice.sqlite")
        self.history = []
        self.fail_model = False

        def simulate_openai(key, model, messages):
            assert key == "mock-not-real"
            self.history.append(messages)
            if self.fail_model:
                raise ChatError("ИИ временно недоступен")
            return "Да, я здесь! " + messages[-1]["content"]

        self.transport = simulate_openai
        self.chat = ChatService(self.db, api_key="mock-not-real", transport=self.transport)
        self.queue = TaskQueue(self.db)
        self.server = create_server(self.queue, TOKEN, host="127.0.0.1", port=0, chat_service=self.chat)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.port = self.server.server_address[1]
        self.cid = str(uuid.uuid4())

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)
        self.tmp.cleanup()

    def request(self, method, path, payload=None, valid_token=True):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        headers = {"Content-Type": "application/json"}
        if valid_token:
            headers["Authorization"] = "Bearer " + TOKEN
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
        conn.request(method, path, body, headers)
        result = conn.getresponse()
        response = (result.status, json.loads(result.read().decode("utf-8")))
        conn.close()
        return response

    def test_two_voice_turns_retain_history_through_authenticated_http(self):
        status, first = self.request("POST", "/v1/chat", {
            "conversation_id": self.cid, "text": "Лана, ты тут?"
        })
        self.assertEqual(status, 200)
        self.assertIn("Да, я здесь!", first["answer"])
        status, second = self.request("POST", "/v1/chat", {
            "conversation_id": self.cid, "text": "Что ты помнишь?"
        })
        self.assertEqual(status, 200)
        self.assertEqual(second["conversation_id"], self.cid)
        self.assertEqual([m["role"] for m in self.history[1]],
                         ["user", "assistant", "user"])
        self.assertEqual(self.history[1][0]["content"], "Лана, ты тут?")
        self.assertEqual(self.history[1][-1]["content"], "Что ты помнишь?")
        with sqlite3.connect(self.db) as con:
            rows = con.execute("SELECT role, text FROM chat_messages WHERE conversation_id=?",
                               (self.cid,)).fetchall()
        self.assertEqual(len(rows), 4)

    def test_approved_project_memory_is_used_after_restart(self):
        self.chat.memory.set("projects", "gold_mode", "GOLD только DEMO", approved=True)
        code, first = self.request("POST", "/v1/chat", {
            "conversation_id": self.cid, "text": "Что с GOLD?"
        })
        self.assertEqual(code, 200)
        self.assertEqual(self.history[0][0]["role"], "developer")
        self.assertIn("GOLD только DEMO", self.history[0][0]["content"])
        # Restart chat service with the SAME persistent database.
        restarted = ChatService(self.db, api_key="mock-not-real", transport=self.transport)
        self.assertIn("GOLD только DEMO", restarted.memory.context())
        answer = restarted.answer(self.cid, "Продолжим.")
        self.assertIn("Продолжим.", answer)
        self.assertEqual(self.history[-1][-3]["role"], "user")
        self.assertEqual(self.history[-1][-3]["content"], "Что с GOLD?")
        self.assertEqual(len(restarted.memory.all()), 1)

    def test_unauthorized_commands_and_memory_write_api_rejected(self):
        self.assertEqual(self.request("POST", "/v1/chat", {
            "conversation_id": self.cid, "text": "Привет"
        }, valid_token=False)[0], 401)
        self.assertEqual(self.request("POST", "/v1/tasks", {
            "kind": "real_trade", "payload": {"action": "BUY"}
        })[0], 403)
        self.assertEqual(self.request("POST", "/v1/memory", {
            "scope": "assistant", "key": "admin", "value": "injected"
        })[0], 404)
        self.assertEqual(self.request("POST", "/v1/chat", {
            "conversation_id": self.cid, "text": "", "unapproved": "metadata"
        })[0], 400)
        self.assertEqual(self.chat.memory.all(), [])
        self.assertEqual(self.history, [])

    def test_model_failure_does_not_save_fictional_history(self):
        self.fail_model = True
        status, result = self.request("POST", "/v1/chat", {
            "conversation_id": self.cid, "text": "Забронируй без подтверждения"
        })
        self.assertEqual(status, 503)
        self.assertIn("недоступен", result["error"])
        with sqlite3.connect(self.db) as con:
            count = con.execute("SELECT COUNT(*) FROM chat_messages").fetchone()[0]
        self.assertEqual(count, 0)
        self.fail_model = False
        self.assertEqual(self.request("POST", "/v1/chat", {
            "conversation_id": self.cid, "text": "Теперь просто ответь"
        })[0], 200)
        self.assertEqual(len(self.history), 2)


if __name__ == "__main__":
    unittest.main()
