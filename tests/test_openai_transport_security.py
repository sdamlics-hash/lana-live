"""Security regression: the OpenAI bearer key must never follow HTTP redirects.

All HTTP requests in this suite go to disposable loopback servers; no real
provider API, credential, tablet or VM is contacted.
"""
import http.server
import json
import threading
import unittest
from unittest.mock import patch

from lana_core import chat


class _QuietServer(http.server.ThreadingHTTPServer):
    daemon_threads = True


class OpenAITransportSecurityTests(unittest.TestCase):
    def setUp(self):
        self.redirect_hits = []
        self.target_hits = []
        self.status = 200
        self.body = json.dumps({
            "output": [{"content": [{"type": "output_text", "text": "Привет, Сантик!"}]}]
        }, ensure_ascii=False).encode("utf-8")
        outer = self

        class Target(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                outer.target_hits.append(self.headers.get("Authorization"))
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b"redirect target")
            do_POST = do_GET
            def log_message(self, *_): pass

        self.target = _QuietServer(("127.0.0.1", 0), Target)
        self.target_thread = threading.Thread(target=self.target.serve_forever, daemon=True)
        self.target_thread.start()

        class Provider(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                outer.redirect_hits.append(self.headers.get("Authorization"))
                size = int(self.headers.get("Content-Length", "0"))
                self.rfile.read(size)
                self.send_response(outer.status)
                if outer.status in (301, 302, 303, 307, 308):
                    self.send_header("Location",
                                     "http://127.0.0.1:%d/secret" % outer.target.server_port)
                    self.end_headers()
                else:
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", str(len(outer.body)))
                    self.end_headers()
                    self.wfile.write(outer.body)
            def log_message(self, *_): pass

        self.provider = _QuietServer(("127.0.0.1", 0), Provider)
        self.provider_thread = threading.Thread(target=self.provider.serve_forever, daemon=True)
        self.provider_thread.start()
        self.url_patch = patch.object(
            chat, "API_URL", "http://127.0.0.1:%d/v1/responses" % self.provider.server_port
        )
        self.url_patch.start()

    def tearDown(self):
        self.url_patch.stop()
        for server, thread in [(self.provider, self.provider_thread),
                               (self.target, self.target_thread)]:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def ask(self):
        return chat.openai_transport("FAKE-NOT-REAL-KEY", "mock-model", [
            {"role": "user", "content": "Лана, ты тут?"}
        ])

    def test_redirects_never_forward_key_or_speech(self):
        for status in (301, 302, 303, 307, 308):
            with self.subTest(status=status):
                self.status = status
                with self.assertRaises(chat.ChatError) as caught:
                    self.ask()
                self.assertIn("недоступен", str(caught.exception))
                self.assertNotIn("FAKE-NOT-REAL-KEY", str(caught.exception))
                self.assertEqual(self.target_hits, [])
        self.assertEqual(len(self.redirect_hits), 5)
        self.assertTrue(all(h == "Bearer FAKE-NOT-REAL-KEY" for h in self.redirect_hits))

    def test_normal_response_is_accepted(self):
        self.assertEqual(self.ask(), "Привет, Сантик!")
        self.assertEqual(len(self.redirect_hits), 1)
        self.assertEqual(self.target_hits, [])

    def test_provider_response_over_one_megabyte_is_rejected(self):
        self.body = b"x" * (chat.MAX_OPENAI_RESPONSE_BYTES + 1)
        with self.assertRaises(chat.ChatError) as caught:
            self.ask()
        self.assertIn("слишком большой", str(caught.exception))

    def test_malformed_and_unexpected_provider_schema_is_rejected(self):
        for body in (
            b"not json",
            b"[]",
            b'{"output":{}}',
            b'{"output":[{"content":{}}]}',
            b'{"output":[{"content":[null]}]}',
        ):
            with self.subTest(body=body[:40]):
                self.body = body
                with self.assertRaises(chat.ChatError) as caught:
                    self.ask()
                self.assertIn("некорректный", str(caught.exception))

    def test_empty_text_does_not_become_fake_success(self):
        self.body = b'{"output":[{"content":[{"type":"output_text","text":"   "}]}]}'
        with self.assertRaises(chat.ChatError) as caught:
            self.ask()
        self.assertIn("пустым", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
