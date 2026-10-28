"""Offline acceptance checks for LANA's HTTPS + Bearer staging contract.

No real credentials, no provider calls, no production host or Android device.
"""
import io
import json
import shutil
import ssl
import subprocess
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from email.message import Message
from pathlib import Path

from lana_core.api import create_server
from lana_core.engine import TaskQueue
from staging.verify_https_auth import AcceptanceError, RejectRedirects, verify_https_auth

TOKEN = "test-only-" + "X" * 40
ROOT = "https://lana.example.test"
STATUS = {"ok": True, "mode": "offline-prototype",
          "allowed": ["heartbeat", "trade_report"]}
CONNECTIONS = {
    "schema": "lana.connections.v1",
    "connections": {
        "openai_api": {"state": "unverified"},
        "chatgpt_history": {"state": "not_connected"},
        "lana_memory": {"state": "readable"},
    },
    "chat_paths": ["/v1/chat", "/v1/lana/chat"],
}


class Response(io.BytesIO):
    def __init__(self, data, url, content_type="application/json"):
        super().__init__(data)
        self.status = 200
        self._url = url
        self.headers = Message()
        self.headers["Content-Type"] = content_type

    def geturl(self):
        return self._url


def ok(data, path):
    return Response(json.dumps(data).encode("utf-8"), ROOT + path)


def error(code):
    return urllib.error.HTTPError(ROOT, code, "ignored", {}, None)


class FakeClient:
    def __init__(self, replies):
        self.replies = list(replies)
        self.calls = []

    def open(self, request, timeout):
        self.calls.append((request, timeout))
        response = self.replies.pop(0)
        if isinstance(response, Exception):
            raise response
        return response


class ProbeContractTests(unittest.TestCase):
    def valid(self):
        return FakeClient([error(401), ok(STATUS, "/v1/status"),
                           ok(CONNECTIONS, "/v1/connections")])

    def test_read_only_contract_and_no_secret_in_unauthorized_probe(self):
        client = self.valid()
        result = verify_https_auth(ROOT, TOKEN, client)
        self.assertTrue(result["https_and_certificate_verified"])
        self.assertTrue(result["connections_schema_verified"])
        self.assertFalse(result["live_openai_tested"])
        self.assertFalse(result["samsung_audio_tested"])
        self.assertEqual(result["paid_model_requests"], 0)
        self.assertEqual(len(client.calls), 3)
        self.assertTrue(all(req.get_method() == "GET" and timeout == 8
                            for req, timeout in client.calls))
        self.assertNotIn("Authorization", client.calls[0][0].headers)
        self.assertEqual(client.calls[1][0].get_header("Authorization"), "Bearer " + TOKEN)
        self.assertEqual(client.calls[2][0].get_header("Authorization"), "Bearer " + TOKEN)
        self.assertEqual(client.calls[2][0].full_url, ROOT + "/v1/connections")

    def test_public_status_or_invalid_token_rejected(self):
        for replies in ([ok(STATUS, "/v1/status")],
                        [error(401), error(403)]):
            with self.subTest(replies=replies), self.assertRaises(AcceptanceError):
                verify_https_auth(ROOT, TOKEN, FakeClient(replies))

    def test_unrecognized_connections_are_not_reported_as_verified(self):
        for bad in [
            {"schema": "unexpected", "connections": CONNECTIONS["connections"], "chat_paths": ["/v1/chat"]},
            {"schema": "lana.connections.v1", "connections": {}, "chat_paths": ["/v1/chat"]},
            {"schema": "lana.connections.v1", "connections": CONNECTIONS["connections"], "chat_paths": []},
        ]:
            with self.subTest(bad=bad), self.assertRaises(AcceptanceError):
                verify_https_auth(ROOT, TOKEN,
                    FakeClient([error(401), ok(STATUS, "/v1/status"), ok(bad, "/v1/connections")]))

    def test_redirect_and_wrong_endpoint_never_accepted(self):
        self.assertIsNone(RejectRedirects().redirect_request(None, None, 302, "", {}, "https://evil.test"))
        with self.assertRaises(AcceptanceError):
            verify_https_auth(ROOT, TOKEN, FakeClient([error(302)]))
        with self.assertRaises(AcceptanceError):
            verify_https_auth(ROOT, TOKEN, FakeClient([
                error(401), Response(json.dumps(STATUS).encode(), "https://other.test/v1/status")
            ]))

    def test_reject_invalid_urls_before_network(self):
        for url in ["http://lana.example.test", "https://user:pass@lana.example.test",
                    "https://lana.example.test/v1/chat", "https://lana.example.test/?key=x",
                    "https://lana.example.test/#fragment", "https://lana.example.test:bad",
                    "https://lana.example.test:0", "https://lana.example.test\\evil",
                    "https://lana.example.test\nX-Bad: yes"]:
            with self.subTest(url=url), self.assertRaises(AcceptanceError):
                verify_https_auth(url, TOKEN, FakeClient([]))

    def test_reject_bad_tokens_without_network(self):
        for token in ["", "short", "X" * 31, "X" * 35 + "\n", "X" * 35 + "é"]:
            with self.subTest(token=token), self.assertRaises(AcceptanceError):
                verify_https_auth(ROOT, token, FakeClient([]))

    def test_response_bounds_content_type_and_json_schema(self):
        bad_replies = [
            Response(b"X" * 8193, ROOT + "/v1/status"),
            Response(b"{}", ROOT + "/v1/status", "text/html"),
            Response(b"{", ROOT + "/v1/status"),
            Response(b"[]", ROOT + "/v1/status"),
            ok({"ok": True, "mode": "offline-prototype", "allowed": ["trade_report"]}, "/v1/status"),
        ]
        for bad in bad_replies:
            with self.subTest(bad=bad), self.assertRaises(AcceptanceError):
                verify_https_auth(ROOT, TOKEN, FakeClient([error(401), bad]))

    def test_interrupted_response_read_does_not_leak_secrets(self):
        class Interrupted(Response):
            def read(self, *args):
                raise OSError("sensitive response " + TOKEN)
        with self.assertRaises(AcceptanceError) as cm:
            verify_https_auth(ROOT, TOKEN, FakeClient([
                error(401), Interrupted(b"", ROOT + "/v1/status")
            ]))
        self.assertNotIn(TOKEN, str(cm.exception))
        self.assertIn("прервался", str(cm.exception))

    def test_network_errors_do_not_leak_token(self):
        with self.assertRaises(AcceptanceError) as cm:
            verify_https_auth(ROOT, TOKEN, FakeClient([OSError(TOKEN)]))
        self.assertNotIn(TOKEN, str(cm.exception))


class RealTLSContractTests(unittest.TestCase):
    def test_real_loopback_tls_untrusted_certificate_rejected(self):
        if not shutil.which("openssl"):
            self.skipTest("openssl not installed")
        with tempfile.TemporaryDirectory() as folder:
            cert = str(Path(folder) / "cert.pem")
            key = str(Path(folder) / "key.pem")
            subprocess.run([
                "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                "-keyout", key, "-out", cert, "-days", "1", "-subj", "/CN=127.0.0.1",
                "-addext", "subjectAltName=IP:127.0.0.1",
            ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            server = create_server(TaskQueue(str(Path(folder) / "tasks.sqlite")), TOKEN, port=0)
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            context.load_cert_chain(cert, key)
            server.socket = context.wrap_socket(server.socket, server_side=True)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            root = "https://127.0.0.1:" + str(server.server_address[1])
            try:
                with self.assertRaises(AcceptanceError):
                    verify_https_auth(root, TOKEN)
                trusted = urllib.request.build_opener(
                    urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=cert)),
                    RejectRedirects(),
                )
                result = verify_https_auth(root, TOKEN, trusted)
                self.assertTrue(result["unauthorized_access_rejected"])
                self.assertEqual(result["server_claims"]["openai_api"], "not_configured")
                self.assertFalse(result["live_openai_tested"])
                with self.assertRaises(AcceptanceError):
                    verify_https_auth(root, "wrong-" + "Y" * 40, trusted)
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=4)


if __name__ == "__main__":
    unittest.main()
