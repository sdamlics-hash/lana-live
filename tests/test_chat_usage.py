import concurrent.futures
import http.client
import json
import tempfile
import threading
import unittest
import uuid
from pathlib import Path

from lana_core.api import create_server
from lana_core.chat import ChatService, ChatError
from lana_core.engine import TaskQueue
from lana_core.usage import UsageGate, UsageLimitError

TOKEN = "local-usage-test-" + "abcdefghijklmnopqrstuvwxyz0123456789"
TIME = 1761019200.0


class UsageGuardTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db = str(Path(self.tmp.name) / "queue.sqlite")
        self.cid = str(uuid.uuid4())
        self.current_time = TIME

    def tearDown(self):
        self.tmp.cleanup()

    def gate(self, daily=5, hourly=2):
        return UsageGate(self.db, daily_limit=daily,
                         conversation_hourly_limit=hourly,
                         clock=lambda: self.current_time)

    def test_daily_and_conversation_hourly_limits(self):
        g = self.gate(daily=3, hourly=2)
        g.claim(self.cid)
        g.claim(self.cid)
        with self.assertRaises(UsageLimitError):
            g.claim(self.cid)
        self.assertEqual(g.status()["attempts_today"], 2)
        g.claim(str(uuid.uuid4()))
        with self.assertRaises(UsageLimitError):
            g.claim(str(uuid.uuid4()))
        self.assertEqual(g.status()["attempts_today"], 3)

    def test_quota_state_survives_restart(self):
        self.gate(daily=2, hourly=9).claim(self.cid)
        new_process = self.gate(daily=2, hourly=9)
        new_process.claim(self.cid)
        with self.assertRaises(UsageLimitError):
            new_process.claim(self.cid)

    def test_hourly_reset_and_daily_reset(self):
        g = self.gate(daily=2, hourly=1)
        g.claim(self.cid)
        with self.assertRaises(UsageLimitError):
            g.claim(self.cid)
        self.current_time = (int(TIME // 3600) + 1) * 3600 + 5
        g.claim(self.cid)
        self.assertEqual(g.status()["attempts_today"], 2)
        with self.assertRaises(UsageLimitError):
            g.claim(self.cid)
        self.current_time = (int(TIME // 86400) + 1) * 86400 + 5
        g.claim(self.cid)
        self.assertEqual(g.status()["attempts_today"], 1)

    def test_rejection_does_not_charge_other_quota(self):
        g = self.gate(daily=5, hourly=1)
        g.claim(self.cid)
        for _ in range(3):
            with self.assertRaises(UsageLimitError):
                g.claim(self.cid)
        self.assertEqual(g.status()["attempts_today"], 1)

    def test_atomic_parallel_claims_cap_total(self):
        g = self.gate(daily=4, hourly=10)
        def attempt(i):
            try:
                g.claim(f"client-{i}")
                return True
            except UsageLimitError:
                return False
        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
            actual = list(pool.map(attempt, range(20)))
        self.assertEqual(sum(actual), 4)
        self.assertEqual(g.status()["attempts_today"], 4)

    def test_invalid_configuration_fails_closed(self):
        for daily, hourly in [(0, 10), (10, -1), ("5", 1), (True, 1), (10001, 10)]:
            with self.assertRaises(ValueError):
                UsageGate(self.db, daily_limit=daily, conversation_hourly_limit=hourly)

    def test_billing_attempt_consumed_even_on_transport_error(self):
        calls = []
        def failing(*args):
            calls.append(1)
            raise ChatError("service unavailable")
        chat = ChatService(self.db, api_key="fake-secret", transport=failing)
        chat.usage.daily_limit = 2
        chat.usage.conversation_hourly_limit = 2
        with self.assertRaises(ChatError):
            chat.answer(self.cid, "Первое")
        with self.assertRaises(ChatError):
            chat.answer(self.cid, "Второе")
        with self.assertRaises(UsageLimitError):
            chat.answer(self.cid, "Третье")
        self.assertEqual(len(calls), 2)
        self.assertEqual(chat.usage.status()["attempts_today"], 2)

    def test_voice_http_returns_429_and_saves_only_accepted_turn(self):
        calls = []
        def model(*args):
            calls.append(1)
            return "Отвечаю по-русски"
        chat = ChatService(self.db, api_key="fake-secret", transport=model)
        chat.usage.daily_limit = 1
        chat.usage.conversation_hourly_limit = 1
        server = create_server(TaskQueue(self.db), TOKEN, port=0, chat_service=chat)
        runner = threading.Thread(target=server.serve_forever, daemon=True)
        runner.start()

        def request(question):
            conn = http.client.HTTPConnection("127.0.0.1", server.server_address[1], timeout=5)
            body = json.dumps({"conversation_id": self.cid, "text": question},
                              ensure_ascii=False).encode("utf-8")
            conn.request("POST", "/v1/chat", body, {
                "Content-Type": "application/json",
                "Authorization": "Bearer " + TOKEN,
            })
            response = conn.getresponse()
            code = response.status
            data = json.loads(response.read().decode("utf-8"))
            conn.close()
            return code, data
        try:
            first = request("Лана, ты тут?")
            second = request("Ещё раз")
            self.assertEqual(first[0], 200)
            self.assertEqual(second[0], 429)
            self.assertIn("Лимит", second[1]["error"])
            self.assertEqual(len(calls), 1)
            with chat._connect() as con:
                count = con.execute("SELECT COUNT(*) FROM chat_messages").fetchone()[0]
            self.assertEqual(count, 2)
        finally:
            server.shutdown()
            server.server_close()
            runner.join(timeout=5)

if __name__ == "__main__":
    unittest.main()
