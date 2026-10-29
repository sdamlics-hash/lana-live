"""Read-only HTTPS + Bearer acceptance probe for a future isolated LANA staging server.

No paid model calls, memory writes, camera/microphone access, or real trades.
Run ONLY against a staging endpoint explicitly controlled by the user.
"""
import argparse
import http.client
import json
import os
import ssl
import urllib.error
import urllib.parse
import urllib.request


class AcceptanceError(RuntimeError):
    """Sanitized failure; never include the address, secret, or response body."""


class RejectRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _endpoint(base_url):
    if not isinstance(base_url, str) or not base_url or any(
        ord(c) < 33 or ord(c) == 127 or c == "\\" for c in base_url
    ):
        raise AcceptanceError("Некорректный адрес: нужен отдельный HTTPS-домен")
    try:
        parsed = urllib.parse.urlsplit(base_url)
        port = parsed.port
    except (TypeError, ValueError):
        raise AcceptanceError("Некорректный HTTPS-адрес") from None
    if (parsed.scheme != "https" or not parsed.hostname or
            parsed.username is not None or parsed.password is not None or
            parsed.query or parsed.fragment or parsed.path not in ("", "/") or
            port == 0 or "@" in parsed.netloc):
        raise AcceptanceError("Нужен HTTPS-адрес без пути, логина и параметров")
    return base_url.rstrip("/")


def _request(opener, endpoint, token=None):
    headers = {"Accept": "application/json"}
    if token is not None:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(endpoint, headers=headers, method="GET")
    try:
        response = opener.open(request, timeout=8)
    except urllib.error.HTTPError as exc:
        code = exc.code
        exc.close()
        return code, None
    except (OSError, ValueError, http.client.HTTPException, urllib.error.URLError):
        raise AcceptanceError("HTTPS-подключение или сертификат не подтверждены") from None
    with response:
        if response.geturl() != endpoint:
            raise AcceptanceError("Перенаправление запрещено")
        if response.status != 200:
            return response.status, None
        if response.headers.get_content_type() != "application/json":
            raise AcceptanceError("Неверный формат ответа сервера")
        try:
            raw = response.read(8193)
            if len(raw) > 8192:
                raise AcceptanceError("Ответ сервера слишком большой")
            payload = json.loads(raw.decode("utf-8"))
        except (OSError, http.client.HTTPException, urllib.error.URLError):
            raise AcceptanceError("Ответ HTTPS-сервера прервался") from None
        except (UnicodeError, ValueError, TypeError):
            raise AcceptanceError("Неверный JSON сервера") from None
        if not isinstance(payload, dict):
            raise AcceptanceError("Неверный JSON сервера")
        return 200, payload


def verify_https_auth(base_url, token, opener=None):
    """Probe GET /v1/status and /v1/connections without/with token.

    Opener injection is for offline tests with a trusted local TLS certificate.
    """
    root = _endpoint(base_url)
    if (not isinstance(token, str) or len(token) < 32 or
            any(not 33 <= ord(ch) <= 126 for ch in token)):
        raise AcceptanceError("Нужен отдельный токен приложения Ланы")
    opener = opener or urllib.request.build_opener(
        urllib.request.HTTPSHandler(context=ssl.create_default_context()),
        RejectRedirects(),
    )
    # Each exposed route must independently reject an unauthenticated client.
    # Otherwise a misconfigured reverse proxy can leak connection metadata
    # even while /v1/status is correctly protected.
    for path in ("/v1/status", "/v1/connections"):
        code, _ = _request(opener, root + path)
        if code != 401:
            raise AcceptanceError("Проверка защиты провалена: без токена ожидается 401")
    code, status = _request(opener, root + "/v1/status", token)
    if code != 200 or status is None:
        raise AcceptanceError("Токен Ланы отклонён или статус недоступен")
    if (status.get("ok") is not True or status.get("mode") != "offline-prototype" or
            not isinstance(status.get("allowed"), list) or
            any(not isinstance(x, str) for x in status["allowed"]) or
            set(status["allowed"]) != {"heartbeat", "trade_report"}):
        raise AcceptanceError("Схема тестового статуса Ланы не совпадает")
    code, connections = _request(opener, root + "/v1/connections", token)
    if (code != 200 or connections is None or
            connections.get("schema") != "lana.connections.v1" or
            not isinstance(connections.get("connections"), dict) or
            not isinstance(connections.get("chat_paths"), list) or
            "/v1/chat" not in connections["chat_paths"]):
        raise AcceptanceError("Схема подключений Ланы не подтверждена")
    states = connections["connections"]
    for name in ("openai_api", "chatgpt_history", "lana_memory"):
        if not isinstance(states.get(name), dict) or not isinstance(states[name].get("state"), str):
            raise AcceptanceError("Неизвестная схема состояния подключений")
    # These states are only the server's claims. GET does not verify OpenAI,
    # chat, memory writes, speech, device permissions or 24/7 reliability.
    return {
        "https_and_certificate_verified": True,
        "unauthorized_access_rejected": True,
        "unauthorized_connections_rejected": True,
        "authenticated_status_verified": True,
        "connections_schema_verified": True,
        "server_claims": {name: states[name]["state"]
                          for name in ("openai_api", "chatgpt_history", "lana_memory")},
        "live_openai_tested": False,
        "samsung_audio_tested": False,
        "memory_write_tested": False,
        "paid_model_requests": 0,
    }


def main():
    parser = argparse.ArgumentParser(description="LANA: read-only HTTPS staging acceptance")
    parser.add_argument("--url", required=True, help="HTTPS staging root; never include a token")
    args = parser.parse_args()
    try:
        result = verify_https_auth(args.url, os.environ.get("LANA_API_TOKEN", ""))
    except AcceptanceError as exc:
        print(json.dumps({"ok": False, "error": str(exc)}, ensure_ascii=False))
        return 1
    print(json.dumps({"ok": True, **result}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
