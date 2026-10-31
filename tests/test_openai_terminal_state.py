"""Provider terminal-state contract: never speak/store an unfinished response.

Offline tests: patched HTTPS opener, fake credentials, no OpenAI calls.
"""
import http.client
import json
import tempfile
import threading
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

from lana_core.api import create_server
from lana_core.chat import ChatError, ChatService, openai_transport
from lana_core.engine import TaskQueue


TOKEN = "test-terminal-status-" + "x" * 40
CID = "a9d6c840-bb6a-433d-b27f-9fb391edc1b4"


def response(status="completed", item_status="completed", text="Готовый ответ"):
    return {
        "status": status,
        "output": [
            {"type": "reasoning", "summary": []},
            {"type": "message", "role": "assistant", "status": item_status,
             "content": [{"type": "output_text", "text": text}]},
        ],
    }


class MockHttps:
    def __init__(self, payload):
        self.payload = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    def __enter__(self):
        return self
    def __exit__(self, *args):
        pass
    def read(self, _limit):
        return self.payload


class TerminalStateTests(unittest.TestCase):
    def transport(self, payload):
        with patch("lana_core.chat._open_openai_request",
                   side_effect=lambda request, timeout: MockHttps(payload)) as mock:
            try:
                result = openai_transport("fake-not-real-key", "gpt-4.1-mini",
                                          [{"role": "user", "content": "Привет"}])
                return result, mock.call_count, None
            except ChatError as exc:
                return None, mock.call_count, str(exc)

    def test_completed_answer_ignores_non_message_items(self):
        text, calls, err = self.transport(response())
        self.assertEqual(text, "Готовый ответ")
        self.assertEqual(calls, 1)
        self.assertIsNone(err)

    def test_optional_message_status_is_valid(self):
        payload = response()
        del payload["output"][1]["status"]
        self.assertEqual(self.transport(payload)[0], "Готовый ответ")

    def test_incomplete_failed_cancelled_and_pending_are_not_spoken(self):
        for status in ("incomplete", "failed", "cancelled", "in_progress", "queued"):
            with self.subTest(status=status):
                text, calls, err = self.transport(response(status=status))
                self.assertIsNone(text)
                self.assertEqual(calls, 1)
                self.assertIn("не завершил", err)

    def test_missing_status_and_partial_message_rejected(self):
        payload = response()
        del payload["status"]
        self.assertIsNone(self.transport(payload)[0])
        for item_status in ("incomplete", "in_progress", "failed"):
            with self.subTest(item_status=item_status):
                self.assertIsNone(self.transport(response(item_status=item_status))[0])

    def test_no_automatic_retry_no_history_for_incomplete_provider(self):
        with tempfile.TemporaryDirectory() as folder:
            db = str(Path(folder) / "chat.sqlite3")
            chat = ChatService(db, api_key="fake-not-real-key", transport=openai_transport)
            server = create_server(TaskQueue(db), TOKEN, port=0, chat_service=chat)
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            calls = []
            def fake_open(request, timeout):
                calls.append(request)
                return MockHttps(response(status="incomplete"))
            try:
                with patch("lana_core.chat._open_openai_request", side_effect=fake_open):
                    conn = http.client.HTTPConnection("127.0.0.1", server.server_address[1], timeout=5)
                    body = json.dumps({"conversation_id": CID, "text": "Лана, ты тут?"}).encode("utf-8")
                    conn.request("POST", "/v1/chat", body, {
                        "Content-Type": "application/json", "Authorization": "Bearer " + TOKEN,
                    })
                    res = conn.getresponse()
                    self.assertEqual(res.status, 503)
                    self.assertNotIn("Готовый ответ", res.read().decode("utf-8"))
                    conn.close()
                self.assertEqual(len(calls), 1)
                self.assertEqual(chat.usage.status()["attempts_today"], 1)
                with chat._connect() as dbconn:
                    self.assertEqual(dbconn.execute("SELECT COUNT(*) FROM chat_messages").fetchone()[0], 0)
            finally:
                server.shutdown()
                server.server_close()
                worker.join(timeout=5)


if __name__ == "__main__":
    unittest.main()
