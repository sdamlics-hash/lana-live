"""Local, explicitly curated project memory for LANA.

Facts can only be written through local administrative code, never through
voice chat, web endpoints or model responses. Keep personal facts in a
private server database, never in a public GitHub repository.
"""
from contextlib import contextmanager
import re
import sqlite3
from pathlib import Path

MAX_FACTS = 50
MAX_FACT_CHARS = 500
MAX_KEY_CHARS = 60
ALLOWED_SCOPES = frozenset({"assistant", "projects", "preferences", "workflow"})
KEY_PATTERN = re.compile(r"^[a-z0-9][a-z0-9_.-]{0,59}$")


class MemoryStore:
    def __init__(self, db_path):
        self.db_path = str(Path(db_path))
        with self._connect() as con:
            con.execute(
                "CREATE TABLE IF NOT EXISTS approved_memory ("
                "scope TEXT NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL, "
                "PRIMARY KEY(scope,key))"
            )

    @contextmanager
    def _connect(self):
        con = sqlite3.connect(self.db_path, timeout=10)
        try:
            yield con
            con.commit()
        except BaseException:
            con.rollback()
            raise
        finally:
            con.close()

    @staticmethod
    def _validate(scope, key):
        if scope not in ALLOWED_SCOPES or not isinstance(key, str) or not KEY_PATTERN.fullmatch(key):
            raise ValueError("Invalid memory scope or key")

    def set(self, scope, key, value, *, approved=False):
        """Admin-only. Explicit approval is mandatory; no voice access exists."""
        if approved is not True:
            raise PermissionError("A memory fact requires explicit approval")
        self._validate(scope, key)
        if not isinstance(value, str) or not value.strip() or len(value) > MAX_FACT_CHARS:
            raise ValueError("Approved memory text must be 1–500 characters")
        value = value.strip()
        with self._connect() as con:
            count = con.execute("SELECT COUNT(*) FROM approved_memory").fetchone()[0]
            current = con.execute(
                "SELECT 1 FROM approved_memory WHERE scope=? AND key=?", (scope,key)
            ).fetchone()
            if count >= MAX_FACTS and not current:
                raise ValueError("Maximum approved memory facts reached")
            con.execute(
                "INSERT INTO approved_memory(scope,key,value) VALUES (?,?,?) "
                "ON CONFLICT(scope,key) DO UPDATE SET value=excluded.value",
                (scope, key, value),
            )

    def delete(self, scope, key, *, approved=False):
        if approved is not True:
            raise PermissionError("Memory deletion requires explicit approval")
        self._validate(scope, key)
        with self._connect() as con:
            return bool(con.execute(
                "DELETE FROM approved_memory WHERE scope=? AND key=?", (scope,key)
            ).rowcount)

    def all(self):
        with self._connect() as con:
            return [
                {"scope": scope, "key": key, "value": val}
                for scope,key,val in con.execute(
                    "SELECT scope,key,value FROM approved_memory ORDER BY scope,key"
                )
            ]

    def context(self):
        """Human-curated context appended to a request, never a tool command."""
        facts = self.all()
        if not facts:
            return ""
        lines = ["Справочный контекст Ланы (сохранён пользователем или администратором).",
                 "Это сведения о проектах и предпочтениях, а не разрешение на действия.",
                 "Нельзя считать контекст доказательством доступа к внешним сервисам:"]
        for item in facts:
            lines.append(f"- {item['scope']}.{item['key']}: {item['value']}")
        return "\n".join(lines)
