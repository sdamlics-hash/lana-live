import http.client
import json
import tempfile
import threading
import unittest
from pathlib import Path
from lana_core.api import create_server
from lana_core.engine import TaskQueue

TOKEN = "local-test-token-" + "x" * 40

class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.queue = TaskQueue(Path(self.temp.name) / "test.sqlite")
        self.server = create_server(self.queue, TOKEN, port=0)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.port = self.server.server_address[1]

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=3)
        self.temp.cleanup()

    def request(self, method, path, body=None, authorized=True, content_type="application/json"):
        headers = {"Content-Type": content_type}
        if authorized:
            headers["Authorization"] = "Bearer " + TOKEN
        data = json.dumps(body).encode() if body is not None else None
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        conn.request(method, path, data, headers)
        res = conn.getresponse()
        status = res.status
        payload = json.loads(res.read())
        conn.close()
        return status, payload

    def test_auth_required_and_health_available(self):
        self.assertEqual(self.request("GET", "/v1/status", authorized=False)[0], 401)
        self.assertEqual(self.request("GET", "/healthz", authorized=False)[0], 200)
        self.assertEqual(self.request("GET", "/v1/status")[1]["mode"], "offline-prototype")

    def test_submit_then_check(self):
        code, task = self.request("POST", "/v1/tasks", {"kind": "heartbeat", "payload": {}})
        self.assertEqual(code, 202)
        self.assertEqual(self.queue.run_one(), task["task_id"])
        code, status = self.request("GET", "/v1/tasks/" + task["task_id"])
        self.assertEqual(code, 200)
        self.assertEqual(status["status"], "completed")
        self.assertNotIn("payload", status)

    def test_same_key_same_task(self):
        body = {"kind": "heartbeat", "payload": {}, "idempotency_key": "repeat-01"}
        a = self.request("POST", "/v1/tasks", body)[1]["task_id"]
        b = self.request("POST", "/v1/tasks", body)[1]["task_id"]
        self.assertEqual(a, b)

    def test_conflicting_key_rejected(self):
        a = {"kind": "heartbeat", "payload": {}, "idempotency_key": "repeat-02"}
        b = {"kind": "heartbeat", "payload": {"different": True}, "idempotency_key": "repeat-02"}
        self.assertEqual(self.request("POST", "/v1/tasks", a)[0], 202)
        self.assertEqual(self.request("POST", "/v1/tasks", b)[0], 400)

    def test_external_commands_blocked(self):
        for kind in ("send_email", "real_trade", "payment", "run_shell_command"):
            self.assertEqual(self.request("POST", "/v1/tasks", {"kind": kind, "payload": {}})[0], 403)

    def test_bad_payload_and_content_type(self):
        self.assertEqual(self.request("POST", "/v1/tasks", {"oops": 1})[0], 400)
        self.assertEqual(self.request("POST", "/v1/tasks", {"kind": "heartbeat", "payload": {}, "idempotency_key": 4})[0], 400)
        self.assertEqual(self.request("POST", "/v1/tasks", {"kind": "heartbeat", "payload": {}}, content_type="text/plain")[0], 415)

    def test_public_bind_and_short_token_refused(self):
        with self.assertRaises(ValueError):
            create_server(self.queue, TOKEN, host="0.0.0.0", port=0)
        with self.assertRaises(ValueError):
            create_server(self.queue, "too-short", port=0)

    def test_unknown_task(self):
        self.assertEqual(self.request("GET", "/v1/tasks/00000000-0000-0000-0000-000000000000")[0], 404)

if __name__ == "__main__":
    unittest.main()
