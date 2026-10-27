"""HTTP ingress regression: stalled or ambiguous requests cannot pin the local API.

Disposable loopback socket only. No external traffic, API credentials, or device.
"""
import http.client
import json
import socket
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

from lana_core import api
from lana_core.engine import TaskQueue

TOKEN = "local-http-hardening-test-" + "a" * 40


class LoopbackIngressTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.queue = TaskQueue(Path(self.temp.name) / "ingress.sqlite")
        self.timeout_patch = patch.object(api, "REQUEST_IO_TIMEOUT_SECONDS", 0.25)
        self.timeout_patch.start()
        self.server = api.create_server(self.queue, TOKEN, port=0)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.port = self.server.server_address[1]

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=3)
        self.timeout_patch.stop()
        self.temp.cleanup()

    def raw(self, content):
        with socket.create_connection(("127.0.0.1", self.port), timeout=3) as sock:
            sock.settimeout(3)
            sock.sendall(content)
            pieces = []
            while True:
                data = sock.recv(8192)
                if not data:
                    break
                pieces.append(data)
            return b"".join(pieces)

    def request(self, length_headers, body=b""):
        return self.raw(
            b"POST /v1/tasks HTTP/1.0\r\nHost: localhost\r\n"
            + ("Authorization: Bearer " + TOKEN + "\r\n").encode()
            + b"Content-Type: application/json\r\n"
            + length_headers
            + b"\r\n"
            + body
        )

    def test_stalled_authenticated_body_times_out_and_server_recovers(self):
        # The declared 100 bytes never arrive. Previously this occupied
        # a ThreadingHTTPServer worker indefinitely.
        response = self.request(b"Content-Length: 100\r\n", b"{")
        self.assertTrue(response.startswith(b"HTTP/1.0 408 "), response[:150])
        self.assertIn(b"request body timeout", response)
        self.assertNotIn(TOKEN.encode(), response)
        self.assertEqual(self.queue.run_one(), None)
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=3)
        try:
            conn.request("GET", "/healthz")
            result = conn.getresponse()
            self.assertEqual(result.status, 200)
            self.assertTrue(json.loads(result.read())["ok"])
        finally:
            conn.close()

    def test_duplicate_content_length_is_rejected_before_reading(self):
        response = self.request(
            b"Content-Length: 2\r\nContent-Length: 999\r\n", b"{}"
        )
        self.assertTrue(response.startswith(b"HTTP/1.0 400 "), response[:150])
        self.assertIn(b"duplicate Content-Length", response)
        self.assertIsNone(self.queue.run_one())

    def test_enormous_decimal_content_length_rejected_without_integer_parse(self):
        # Python 3.12 limits integer conversion to 4300 digits; without
        # validation this raised an uncaught exception and closed the socket.
        response = self.request(b"Content-Length: " + b"9" * 5000 + b"\r\n")
        self.assertTrue(response.startswith(b"HTTP/1.0 413 "), response[:150])
        self.assertIn(b"request body too large", response)
        self.assertIsNone(self.queue.run_one())

    def test_normal_valid_post_still_accepts_safe_task(self):
        payload = b'{"kind":"heartbeat","payload":{}}'
        response = self.request(
            b"Content-Length: " + str(len(payload)).encode() + b"\r\n",
            payload,
        )
        self.assertTrue(response.startswith(b"HTTP/1.0 202 "), response[:150])
        self.assertIn(b'"accepted": true', response)


if __name__ == "__main__":
    unittest.main()
