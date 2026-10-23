"""Persistent, atomic call-count protection for LANA's paid model requests.

This limits *attempt count*, not token spending or monetary charges. Requests
that reach the model are counted even if the model call later times out.
It never sends requests or stores user speech, tokens, or API credentials.
"""
from contextlib import contextmanager
from datetime import datetime, timezone
import sqlite3
import time
from pathlib import Path


class UsageLimitError(RuntimeError):
    """The configured model request quota is exhausted."""


class UsageGate:
    def __init__(self, db_path, daily_limit=100, conversation_hourly_limit=30,
                 global_hourly_limit=40, clock=None):
        self.db_path = str(Path(db_path))
        for label, n in [("daily_limit", daily_limit),
                         ("conversation_hourly_limit", conversation_hourly_limit),
                         ("global_hourly_limit", global_hourly_limit)]:
            if type(n) is not int or not 1 <= n <= 10_000:
                raise ValueError(f"{label} must be an integer between 1 and 10000")
        self.daily_limit = daily_limit
        self.conversation_hourly_limit = conversation_hourly_limit
        self.global_hourly_limit = global_hourly_limit
        self.clock = clock or time.time
        with self._connection() as con:
            con.execute(
                "CREATE TABLE IF NOT EXISTS chat_call_usage ("
                "bucket TEXT NOT NULL, scope TEXT NOT NULL, attempts INTEGER NOT NULL,"
                "expires_at REAL NOT NULL, PRIMARY KEY (bucket,scope))"
            )

    @contextmanager
    def _connection(self):
        con = sqlite3.connect(self.db_path, timeout=10)
        try:
            yield con
            con.commit()
        except BaseException:
            con.rollback()
            raise
        finally:
            con.close()

    def claim(self, conversation_id):
        if not isinstance(conversation_id, str) or not conversation_id:
            raise ValueError("A conversation id is required")
        now = float(self.clock())
        if not 0 <= now < 9999999999:
            raise ValueError("Invalid system time")
        now_utc = datetime.fromtimestamp(now, timezone.utc)
        day = now_utc.strftime("%Y-%m-%d")
        hour = now_utc.strftime("%Y-%m-%dT%H")
        day_expires = (int(now // 86400) + 1) * 86400
        hour_expires = (int(now // 3600) + 1) * 3600
        quotas = [
            (f"d:{day}", "all", self.daily_limit, day_expires),
            # A new conversation ID must not reset the hourly protection.
            (f"h:{hour}", "all", self.global_hourly_limit, hour_expires),
            (f"h:{hour}", conversation_id, self.conversation_hourly_limit, hour_expires),
        ]
        with self._connection() as con:
            # Atomic across threads and across independent processes sharing the DB.
            con.execute("BEGIN IMMEDIATE")
            con.execute("DELETE FROM chat_call_usage WHERE expires_at<=?", (now,))
            for bucket, scope, limit, _ in quotas:
                row = con.execute(
                    "SELECT attempts FROM chat_call_usage WHERE bucket=? AND scope=?",
                    (bucket, scope),
                ).fetchone()
                if row is not None and row[0] >= limit:
                    # Neither quota is charged for a rejected request.
                    raise UsageLimitError(
                        "Лимит обращений Ланы к ИИ временно достигнут. Попробуй позднее."
                    )
            for bucket, scope, _, expires in quotas:
                con.execute(
                    "INSERT INTO chat_call_usage(bucket,scope,attempts,expires_at)"
                    " VALUES (?,?,1,?) ON CONFLICT(bucket,scope)"
                    " DO UPDATE SET attempts=attempts+1",
                    (bucket, scope, expires),
                )

    def status(self):
        """Only aggregate attempt count; no speech or IDs are exposed."""
        now = float(self.clock())
        utc = datetime.fromtimestamp(now, timezone.utc)
        bucket = "d:" + utc.strftime("%Y-%m-%d")
        hour_bucket = "h:" + utc.strftime("%Y-%m-%dT%H")
        with self._connection() as con:
            row = con.execute(
                "SELECT attempts FROM chat_call_usage WHERE bucket=? AND scope='all'",
                (bucket,),
            ).fetchone()
            hour_row = con.execute(
                "SELECT attempts FROM chat_call_usage WHERE bucket=? AND scope='all'",
                (hour_bucket,),
            ).fetchone()
        return {"attempts_today": row[0] if row else 0,
                "daily_limit": self.daily_limit,
                "attempts_this_hour": hour_row[0] if hour_row else 0,
                "global_hourly_limit": self.global_hourly_limit,
                "note": "Request-count ceiling only; not a monetary spend limit"}
