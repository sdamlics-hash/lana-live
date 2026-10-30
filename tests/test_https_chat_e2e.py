"""Real local TLS -> Bearer -> chat -> memory/quota acceptance.

Isolated loopback only. An ephemeral self-signed certificate is trusted
explicitly by this TEST client, never by the user's Android device.
The OpenAI transport is always mocked: no provider keys or paid calls.
"""
import http.client
import json
import shutil
import ssl
import subprocess
import tempfile
import threading
import unittest
import uuid
from pathlib import Path

from lana_core.api import create_server
from lana_core.chat import ChatService
from lana_core.engine import TaskQueue


TOKEN = "test-only-https-chat-" + "x" * 40
FAKE_KEY = "not-a-real-openai-key"


@unittest.skipUnless(shutil.which("openssl"), "openssl is required for ephemeral TLS tests")
class HttpsChatAcceptanceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        folder = Path(self.temp.name)
        self.cert = str(folder / "test-cert.pem")
        key = str(folder / "test-key.pem")
        subprocess.run([
            "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
            "-keyout", key, "-out", self.cert, "-days", "1",
            "-subj", "/CN=127.0.0.1", "-addext", "subjectAltName=IP:127.0.0.1",
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=20)
        self.calls = []

        def offline_model(api_key, model, messages):
            self.assertEqual(api_key, FAKE_KEY)
            self.calls.append(messages)
            return "Да, я здесь. Слушаю тебя!"

        db = str(folder / "lana.sqlite3")
        self.chat = ChatService(db, api_key=FAKE_KEY, transport=offline_model)
        self.server = create_server(TaskQueue(db), TOKEN, port=0, chat_service=self.chat)
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.load_cert_chain(self.cert, key)
        self.server.socket = tls.wrap_socket(self.server.socket, server_side=True)
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()
        self.addCleanup(self.stop_server)
        self.port = self.server.server_address[1]
        self.trusted = ssl.create_default_context(cafile=self.cert)
        self.conversation = str(uuid.uuid4())

    def stop_server(self):
        self.server.shutdown()
        self.server.server_close()
        self.worker.join(timeout=5)

    def request(self, method, path, payload=None, token=TOKEN, context=None):
        conn = http.client.HTTPSConnection(
            "127.0.0.1", self.port,
            context=context if context is not None else self.trusted,
            timeout=5,
        )
        headers = {"Accept": "application/json"}
        if token is not None:
            headers["Authorization"] = "Bearer " + token
        body = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        try:
            conn.request(method, path, body=body, headers=headers)
            response = conn.getresponse()
            raw = response.read(8193)
            self.assertLessEqual(len(raw), 8192, "staging response must be bounded")
            self.assertEqual(response.getheader("Cache-Control"), "no-store")
            self.assertEqual(response.getheader("X-Content-Type-Options"), "nosniff")
            return response.status, json.loads(raw.decode("utf-8"))
        finally:
            conn.close()

    def post_chat(self, text, *, path="/v1/chat", conversation=None, token=TOKEN, **extra):
        return self.request("POST", path, {
            "conversation_id": conversation or self.conversation, "text": text, **extra
        }, token=token)

    def test_untrusted_certificate_fails_before_application_or_model(self):
        with self.assertRaises(ssl.SSLCertVerificationError):
            self.request("GET", "/v1/status", context=ssl.create_default_context())
        self.assertEqual(self.calls, [])
        self.assertEqual(self.chat.usage.status()["attempts_today"], 0)
        self.assertEqual(self.request("GET", "/v1/status")[0], 200)

    def test_tls_rejects_missing_and_wrong_bearer_on_chat_and_connections(self):
        for path in ("/v1/chat", "/v1/lana/chat"):
            with self.subTest(path=path):
                self.assertEqual(self.post_chat("Лана, ты тут?", path=path, token=None)[0], 401)
                self.assertEqual(self.post_chat("Лана, ты тут?", path=path, token="wrong-token")[0], 401)
        self.assertEqual(self.request("GET", "/v1/connections", token=None)[0], 401)
        self.assertEqual(self.request("GET", "/v1/status", token=None)[0], 401)
        self.assertEqual(self.calls, [])
        self.assertEqual(self.chat.usage.status()["attempts_today"], 0)

    def test_real_tls_two_russian_turns_both_routes_and_durable_history(self):
        self.chat.memory.set("projects", "gold", "Только DEMO", approved=True)
        code, first = self.post_chat("Лана, ты тут?")
        self.assertEqual(code, 200)
        self.assertIn("Да, я здесь", first["answer"])
        code, second = self.post_chat("Продолжай разговор", path="/v1/lana/chat")
        self.assertEqual(code, 200)
        self.assertEqual(second["conversation_id"], self.conversation)
        self.assertEqual(len(self.calls), 2)
        self.assertEqual([m["role"] for m in self.calls[1]], ["developer", "user", "assistant", "user"])
        self.assertIn("Только DEMO", self.calls[1][0]["content"])
        self.assertEqual(self.calls[1][-1]["content"], "Продолжай разговор")
        self.assertNotIn(FAKE_KEY, json.dumps(first) + json.dumps(second))
        self.assertEqual(self.chat.usage.status()["attempts_today"], 2)
        self.assertEqual(len(self.chat.memory.all()), 1)

    def test_context_notes_require_consent_over_tls(self):
        bad, _ = self.post_chat(
            "Помни заметку", context_notes=["Личное напоминание"]
        )
        self.assertEqual(bad, 400)
        self.assertEqual(len(self.calls), 0)
        good, _ = self.post_chat(
            "Помни заметку", context_notes=["Личное напоминание"],
            memory_consent=True,
        )
        self.assertEqual(good, 200)
        self.assertIn("Личное напоминание", str(self.calls[-1]))
        self.assertEqual(self.chat.usage.status()["attempts_today"], 1)

    def test_global_quota_blocks_rotating_ids_over_tls(self):
        self.chat.usage.global_hourly_limit = 1
        first, _ = self.post_chat("Первый запрос")
        second, body = self.post_chat("Второй запрос", conversation=str(uuid.uuid4()))
        self.assertEqual(first, 200)
        self.assertEqual(second, 429)
        self.assertIn("Лимит", body["error"])
        self.assertEqual(len(self.calls), 1)
        self.assertEqual(self.chat.usage.status()["attempts_today"], 1)

    def test_unapproved_real_trade_is_denied_over_tls(self):
        code, response = self.request("POST", "/v1/tasks", {
            "kind": "real_trade", "payload": {"symbol": "XAUUSD", "action": "BUY"}
        })
        self.assertEqual(code, 403)
        self.assertEqual(response["error"], "task type not approved")
        self.assertEqual(self.calls, [])
        self.assertEqual(self.chat.usage.status()["attempts_today"], 0)


if __name__ == "__main__":
    unittest.main()
