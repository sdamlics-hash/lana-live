import tempfile
import unittest
import uuid
from pathlib import Path
from lana_core.memory import MemoryStore, MAX_FACTS
from lana_core.chat import ChatService


class MemoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.db = Path(self.temp.name) / "memory.sqlite"
        self.store = MemoryStore(self.db)

    def tearDown(self):
        self.temp.cleanup()

    def test_memory_must_be_approved(self):
        with self.assertRaises(PermissionError):
            self.store.set("assistant", "wake_phrase", "Лана, ты тут?")
        with self.assertRaises(PermissionError):
            self.store.delete("assistant", "wake_phrase")

    def test_facts_are_persistent_and_explicit(self):
        self.store.set("assistant", "wake_phrase", "Лана, ты тут?", approved=True)
        self.store.set("projects", "test_mode", "GOLD и Kraken только DEMO", approved=True)
        later=MemoryStore(self.db)
        facts=later.all()
        self.assertEqual(len(facts),2)
        self.assertIn("Лана, ты тут?", later.context())
        self.assertIn("не разрешение на действия", later.context())

    def test_memory_deletion_needs_approval(self):
        self.store.set("assistant","wake_phrase","Лана",approved=True)
        with self.assertRaises(PermissionError):
            self.store.delete("assistant","wake_phrase")
        self.assertTrue(self.store.delete("assistant","wake_phrase",approved=True))
        self.assertEqual(self.store.context(),"")

    def test_invalid_input_and_limit(self):
        for scope,key in [("secrets","api_key"),("assistant","../../escape"),("assistant","CAPS")]:
            with self.assertRaises(ValueError):
                self.store.set(scope,key,"value",approved=True)
        with self.assertRaises(ValueError):
            self.store.set("assistant","empty","",approved=True)
        with self.assertRaises(ValueError):
            self.store.set("assistant","long","a"*501,approved=True)
        for i in range(MAX_FACTS):
            self.store.set("projects",f"test_{i}","ok",approved=True)
        with self.assertRaises(ValueError):
            self.store.set("projects","one_more","ok",approved=True)
        self.store.set("projects","test_0","updated",approved=True)
        self.assertEqual(len(self.store.all()),MAX_FACTS)

    def test_approved_context_used_in_openai_turn(self):
        self.store.set("assistant","tone","Русский язык, спокойно, коротко",approved=True)
        received=[]
        def fake(key,model,messages):
            received.append(messages)
            return "Да, я здесь!"
        chat=ChatService(self.db,api_key="mock-test",transport=fake)
        cid=str(uuid.uuid4())
        result=chat.answer(cid,"Лана, ты тут?")
        self.assertEqual(result,"Да, я здесь!")
        self.assertEqual(received[0][0]["role"],"developer")
        self.assertIn("Русский язык",received[0][0]["content"])
        self.assertEqual(received[0][-1]["role"],"user")
        self.assertEqual(received[0][-1]["content"],"Лана, ты тут?")
        # Context is not recorded as a fictitious user message.
        with chat._connect() as con:
            roles=con.execute("SELECT role FROM chat_messages ORDER BY id").fetchall()
        self.assertEqual([r[0] for r in roles],["user","assistant"])

    def test_chat_does_not_write_memory_automatically(self):
        recorded=[]
        def fake(key,model,messages):
            return "Запомню абсолютно всё"
        chat=ChatService(self.db,api_key="mock-test",transport=fake)
        chat.answer(str(uuid.uuid4()),"Запомни всё")
        self.assertEqual(self.store.all(),[])


if __name__=="__main__":
    unittest.main()
