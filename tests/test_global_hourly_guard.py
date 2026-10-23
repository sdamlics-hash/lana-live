"""Regression tests: rotating conversation IDs must not bypass hourly limits."""
import concurrent.futures
import http.client
import json
import tempfile
import threading
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

from lana_core.api import create_server
from lana_core.chat import ChatService
from lana_core.engine import TaskQueue
from lana_core.usage import UsageGate, UsageLimitError


TOKEN = "hourly-quota-test-" + "abcdef1234567890abcdef1234567890"
FIXED_TIME = 1761019200.0


class GlobalHourlyGuardTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db = str(Path(self.tmp.name) / "lana.sqlite")
        self.now = FIXED_TIME

    def tearDown(self):
        self.tmp.cleanup()

    def gate(self, daily=100, hourly=3, per_conversation=30):
        return UsageGate(
            self.db, daily_limit=daily, global_hourly_limit=hourly,
            conversation_hourly_limit=per_conversation, clock=lambda: self.now,
        )

    def test_rotating_conversation_ids_cannot_reset_shared_hourly_cap(self):
        guard = self.gate(hourly=3)
        for _ in range(3):
            guard.claim(str(uuid.uuid4()))
        with self.assertRaises(UsageLimitError):
            guard.claim(str(uuid.uuid4()))
        self.assertEqual(guard.status()["attempts_this_hour"], 3)
        self.assertEqual(guard.status()["attempts_today"], 3)

    def test_cap_survives_service_restart_and_rejected_calls_are_free(self):
        self.gate(hourly=1).claim(str(uuid.uuid4()))
        new_instance = self.gate(hourly=1)
        for _ in range(5):
            with self.assertRaises(UsageLimitError):
                new_instance.claim(str(uuid.uuid4()))
        self.assertEqual(new_instance.status()["attempts_today"], 1)
        self.assertEqual(new_instance.status()["attempts_this_hour"], 1)

    def test_global_hour_resets_but_daily_limit_still_applies(self):
        guard = self.gate(daily=2, hourly=1)
        guard.claim(str(uuid.uuid4()))
        self.now = (int(self.now // 3600) + 1) * 3600 + 2
        guard.claim(str(uuid.uuid4()))
        self.assertEqual(guard.status()["attempts_this_hour"], 1)
        with self.assertRaises(UsageLimitError):
            guard.claim(str(uuid.uuid4()))
        self.now = (int(self.now // 86400) + 1) * 86400 + 2
        guard.claim(str(uuid.uuid4()))
        self.assertEqual(guard.status()["attempts_today"], 1)

    def test_atomic_hourly_cap_across_parallel_new_conversations(self):
        guard = self.gate(hourly=4)
        def try_claim(_):
            try:
                guard.claim(str(uuid.uuid4()))
                return True
            except UsageLimitError:
                return False
        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
            results = list(pool.map(try_claim, range(24)))
        self.assertEqual(sum(results), 4)
        self.assertEqual(guard.status()["attempts_this_hour"], 4)

    def test_invalid_global_limit_fails_closed(self):
        for value in (0, -1, 10001, "4", True, 1.5):
            with self.assertRaises(ValueError):
                UsageGate(self.db, global_hourly_limit=value)

    def test_http_returns_429_even_when_caller_changes_conversation_id(self):
        calls = []
        def fake_model(*_):
            calls.append(1)
            return "Да, я здесь"
        with patch.dict("os.environ", {"LANA_CHAT_GLOBAL_HOURLY_MAX": "1"}):
            chat = ChatService(self.db, api_key="fake-not-real", transport=fake_model)
        server = create_server(TaskQueue(self.db), TOKEN, port=0, chat_service=chat)
        runner = threading.Thread(target=server.serve_forever, daemon=True)
        runner.start()

        def post():
            conn = http.client.HTTPConnection("127.0.0.1", server.server_address[1], timeout=5)
            payload = json.dumps({"conversation_id": str(uuid.uuid4()),
                                  "text": "Лана, ты тут?"}, ensure_ascii=False).encode()
            conn.request("POST", "/v1/chat", payload, {
                "Authorization": "Bearer " + TOKEN,
                "Content-Type": "application/json",
            })
            response = conn.getresponse()
            code = response.status
            result = json.loads(response.read().decode("utf-8"))
            conn.close()
            return code, result

        try:
            first, _ = post()
            second, error = post()
            self.assertEqual(first, 200)
            self.assertEqual(second, 429)
            self.assertIn("Лимит", error["error"])
            self.assertEqual(len(calls), 1)
        finally:
            server.shutdown()
            server.server_close()
            runner.join(timeout=5)


if __name__ == "__main__":
    unittest.main()
