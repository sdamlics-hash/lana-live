import tempfile
import unittest
import uuid
from pathlib import Path
from lana_core.chat import ChatService, ChatError

class NetworkRecoveryTests(unittest.TestCase):
    def test_failed_turn_not_retried_or_saved_and_context_recovers(self):
        calls = []
        def transport(key, model, messages):
            calls.append(messages)
            if len(calls) == 2:
                raise ChatError("simulated network failure")
            return "Ответ"
        with tempfile.TemporaryDirectory() as tmp:
            service = ChatService(Path(tmp) / "chat.db", api_key="mock", transport=transport)
            cid = str(uuid.uuid4())
            service.answer(cid, "Начальная тема")
            with self.assertRaises(ChatError):
                service.answer(cid, "Неудачная реплика")
            self.assertEqual(len(calls), 2)
            service.answer(cid, "Продолжим тему")
            self.assertEqual(len(calls), 3)
            self.assertEqual([m["content"] for m in calls[-1]],
                             ["Начальная тема", "Ответ", "Продолжим тему"])
            with service._connect() as con:
                self.assertEqual(con.execute("SELECT COUNT(*) FROM chat_messages").fetchone()[0], 4)
