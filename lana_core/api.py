"""Authenticated loopback HTTP bridge for LANA's offline queue prototype."""
import argparse
import hmac
import json
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit
from .engine import TaskQueue, UnsafeTaskError
from .chat import ChatService, ChatError
from .usage import UsageLimitError

MAX_BODY_BYTES = 1024 * 1024
TASK_ID_RE = re.compile(r"^/v1/tasks/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$")
KEY_RE = re.compile(r"^[A-Za-z0-9_.:-]{1,128}$")

def make_handler(queue, token, chat_service=None):
    if not isinstance(token, str) or len(token) < 32:
        raise ValueError("LANA_API_TOKEN must be a random secret of at least 32 characters")

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):
            # Do not record sensitive URLs, bodies, or authorization headers.
            return

        def _json(self, code, value):
            body = json.dumps(value, ensure_ascii=False, allow_nan=False).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.end_headers()
            self.wfile.write(body)

        def _authorized(self):
            supplied = self.headers.get("Authorization", "")
            return hmac.compare_digest(supplied.encode("utf-8"), ("Bearer " + token).encode("utf-8"))

        def _path(self):
            url = urlsplit(self.path)
            return url.path if not url.query and not url.fragment else None

        def _require_auth(self):
            if not self._authorized():
                self._json(401, {"error": "authentication required"})
                return False
            return True

        def do_GET(self):
            path = self._path()
            if path == "/healthz":
                self._json(200, {"ok": True, "service": "lana-core-bridge"})
                return
            if not self._require_auth():
                return
            if path == "/v1/connections":
                connections = chat_service.connection_status() if chat_service is not None else {
                    "openai_api": {"state": "not_configured"},
                    "chatgpt_history": {"state": "not_connected"},
                    "lana_memory": {"state": "unavailable", "scope": "server"}}
                self._json(200, {"schema": "lana.connections.v1", "connections": connections,
                                 "chat_paths": ["/v1/chat", "/v1/lana/chat"],
                                 "chat_schema": "conversation_id,text,optional:context_notes,memory_consent"})
                return
            if path == "/v1/status":
                self._json(200, {"ok": True, "mode": "offline-prototype",
                                 "allowed": ["heartbeat", "trade_report"]})
                return
            match = TASK_ID_RE.fullmatch(path or "")
            if match:
                task = queue.get(match.group(1))
                if task is None:
                    self._json(404, {"error": "task not found"})
                else:
                    self._json(200, {"task_id": task["task_id"], "kind": task["kind"],
                                     "status": task["status"], "result": task["result"],
                                     "error": task["error"], "attempts": task["attempts"]})
                return
            self._json(404, {"error": "unknown endpoint"})

        def do_POST(self):
            if not self._require_auth():
                return
            path = self._path()
            if path not in ("/v1/tasks", "/v1/chat", "/v1/lana/chat"):
                self._json(404, {"error": "unknown endpoint"})
                return
            if self.headers.get("Transfer-Encoding"):
                self._json(400, {"error": "chunked input is not supported"})
                return
            if self.headers.get("Content-Type", "").split(";")[0].strip().lower() != "application/json":
                self._json(415, {"error": "application/json required"})
                return
            length_str = self.headers.get("Content-Length", "")
            if not length_str.isascii() or not length_str.isdecimal():
                self._json(411, {"error": "Content-Length required"})
                return
            length = int(length_str)
            if length == 0:
                self._json(400, {"error": "empty request"})
                return
            if length > MAX_BODY_BYTES:
                self._json(413, {"error": "request body too large"})
                return
            try:
                data = json.loads(self.rfile.read(length).decode("utf-8"),
                                  parse_constant=lambda _: (_ for _ in ()).throw(ValueError("non-finite JSON")))
            except (UnicodeError, ValueError):
                self._json(400, {"error": "invalid JSON"})
                return
            if path in ("/v1/chat", "/v1/lana/chat"):
                if (not isinstance(data, dict) or not {"conversation_id", "text"}.issubset(data)
                    or not set(data).issubset({"conversation_id", "text", "context_notes", "memory_consent"})):
                    self._json(400, {"error": "invalid chat schema"})
                    return
                if chat_service is None:
                    self._json(503, {"error": "LANA chat is not yet configured"})
                    return
                try:
                    answer = chat_service.answer(data["conversation_id"], data["text"],
                        context_notes=data.get("context_notes"), memory_consent=data.get("memory_consent", False))
                except ValueError:
                    self._json(400, {"error": "invalid conversation id or text"})
                    return
                except UsageLimitError as exc:
                    self._json(429, {"error": str(exc)})
                    return
                except ChatError as exc:
                    self._json(503, {"error": str(exc)})
                    return
                self._json(200, {"answer": answer, "conversation_id": data["conversation_id"]})
                return
            if (not isinstance(data, dict) or
                not {"kind", "payload"}.issubset(data) or
                not set(data).issubset({"kind", "payload", "idempotency_key"})):
                self._json(400, {"error": "expected kind, payload and optional idempotency_key"})
                return
            kind, payload, key = data["kind"], data["payload"], data.get("idempotency_key")
            if (not isinstance(kind, str) or not isinstance(payload, dict) or
                (key is not None and (not isinstance(key, str) or KEY_RE.fullmatch(key) is None))):
                self._json(400, {"error": "invalid task schema"})
                return
            try:
                task_id = queue.enqueue(kind, payload, key)
            except UnsafeTaskError:
                self._json(403, {"error": "task type not approved"})
                return
            except (ValueError, TypeError, OverflowError):
                self._json(400, {"error": "invalid payload or conflicting idempotency key"})
                return
            self._json(202, {"accepted": True, "task_id": task_id})

    return Handler

class LoopbackServer(ThreadingHTTPServer):
    daemon_threads = True

def create_server(queue, token, host="127.0.0.1", port=8787, chat_service=None):
    if host != "127.0.0.1":
        raise ValueError("Only IPv4 loopback is allowed; use a reviewed TLS/VPN proxy")
    return LoopbackServer((host, port), make_handler(queue, token, chat_service))

def main():
    parser = argparse.ArgumentParser(description="LANA offline-only local task API")
    parser.add_argument("--db", default="lana_tasks.sqlite3")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8787)
    args = parser.parse_args()
    server = create_server(TaskQueue(args.db), os.environ.get("LANA_API_TOKEN", ""),
                           args.host, args.port, ChatService(args.db))
    try:
        print(f"LANA core API on {args.host}:{server.server_address[1]} (loopback only)")
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

if __name__ == "__main__":
    main()
