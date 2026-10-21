"""LANA's Russian voice dialogue backed by OpenAI Responses API.

The module stores only a short rolling conversation locally, never an API key.
It has no access to Gmail, the desktop, trading accounts, or shell commands.
"""
import json
import os
import sqlite3
import threading
import uuid
import urllib.error
import urllib.request
from pathlib import Path

MAX_INPUT_CHARS = 2400
MAX_HISTORY_MESSAGES = 16
MAX_ANSWER_CHARS = 5000
DEFAULT_MODEL = "gpt-4.1-mini"
API_URL = "https://api.openai.com/v1/responses"

PERSONA = (
    "Ты Лана — русскоязычная голосовая помощница пользователя. "
    "Разговаривай естественно, по делу, с уместным юмором. "
    "Отвечай кратко, пока не попросят подробнее. "
    "Ты отдельное приложение и не имеешь автоматически доступ к ChatGPT-перепискам, "
    "почте, серверу, телефону и торговым роботам. "
    "Не придумывай выполненных действий или подключений. "
    "Если речь о деньгах, договорах, реальных сделках или удалении данных, "
    "попроси подтверждение до любого действия. "
    "На «Лана, ты тут?» можно ответить: «Да, я здесь. Слушаю тебя!»"
)

class ChatError(RuntimeError):
    pass


def openai_transport(api_key, model, messages, *, timeout=28):
    """One API call; system prompt and recent history are sent with store=false."""
    payload = {
        "model": model,
        "instructions": PERSONA,
        "input": messages,
        "max_output_tokens": 320,
        "store": False,
    }
    request = urllib.request.Request(
        API_URL, method="POST",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": "Bearer " + api_key, "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as res:
            data = json.load(res)
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
        # Do not log error bodies: providers may echo private request data.
        raise ChatError("ИИ сейчас недоступен. Попробуй ещё раз.") from exc

    # Responses API: assistant text lives in output[].content[].text.
    segments = []
    for item in data.get("output", []):
        for part in item.get("content", []):
            if part.get("type") == "output_text" and isinstance(part.get("text"), str):
                segments.append(part["text"])
    answer = "\n".join(segments).strip()
    if not answer:
        raise ChatError("Ответ ИИ был пустым. Попробуй ещё раз.")
    return answer[:MAX_ANSWER_CHARS]


class ChatService:
    def __init__(self, db_path, api_key=None, model=None, transport=None):
        self.db_path = str(Path(db_path))
        self.api_key = api_key if api_key is not None else os.environ.get("OPENAI_API_KEY", "")
        self.model = model or os.environ.get("LANA_OPENAI_MODEL", DEFAULT_MODEL)
        self.transport = transport or openai_transport
        self.lock = threading.RLock()
        with self._connect() as con:
            con.execute("CREATE TABLE IF NOT EXISTS chat_messages ("
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id TEXT NOT NULL,"
                        "role TEXT NOT NULL CHECK(role IN ('user','assistant')),"
                        "text TEXT NOT NULL)")
            con.execute("CREATE INDEX IF NOT EXISTS chat_conv_idx "
                        "ON chat_messages(conversation_id,id)")

    def _connect(self):
        return sqlite3.connect(self.db_path, timeout=10)

    def answer(self, conversation_id, message):
        if not isinstance(conversation_id, str):
            raise ValueError("invalid conversation id")
        try:
            uuid.UUID(conversation_id)
        except (ValueError, AttributeError):
            raise ValueError("invalid conversation id") from None
        if not isinstance(message, str) or not (1 <= len(message.strip()) <= MAX_INPUT_CHARS):
            raise ValueError("invalid message length")
        if not self.api_key:
            raise ChatError("ИИ пока не подключён к серверу: нужен OPENAI_API_KEY.")
        clean = message.strip()

        # Lock serializes same-process calls to avoid merging out-of-order turns.
        with self.lock:
            with self._connect() as con:
                history = con.execute(
                    "SELECT role,text FROM chat_messages WHERE conversation_id=? "
                    "ORDER BY id DESC LIMIT ?",
                    (conversation_id, MAX_HISTORY_MESSAGES),
                ).fetchall()
            context = [{"role": role, "content": text} for role, text in reversed(history)]
            context.append({"role": "user", "content": clean})
            answer = self.transport(self.api_key, self.model, context)
            if not isinstance(answer, str) or not answer.strip():
                raise ChatError("ИИ вернул пустой ответ")
            answer = answer.strip()[:MAX_ANSWER_CHARS]
            with self._connect() as con:
                con.execute(
                    "INSERT INTO chat_messages(conversation_id,role,text) VALUES (?,?,?)",
                    (conversation_id, "user", clean),
                )
                con.execute(
                    "INSERT INTO chat_messages(conversation_id,role,text) VALUES (?,?,?)",
                    (conversation_id, "assistant", answer),
                )
                # Keep at most 16 messages per conversation. No cross-user history.
                con.execute(
                    "DELETE FROM chat_messages WHERE conversation_id=? AND id NOT IN "
                    "(SELECT id FROM chat_messages WHERE conversation_id=? "
                    "ORDER BY id DESC LIMIT ?)",
                    (conversation_id, conversation_id, MAX_HISTORY_MESSAGES),
                )
            return answer
