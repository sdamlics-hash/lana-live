"""An offline-only, auditable prototype of LANA's task runner.

No network clients, brokerage API, email sender, or external command executor
is included. Expanding the allowed task registry requires code review.
"""
import datetime as dt
import json
import math
import sqlite3
import uuid
from pathlib import Path

ALLOWED_TASKS = frozenset({'heartbeat', 'trade_report'})
MAX_TRADES = 100_000

class UnsafeTaskError(ValueError):
    """An unapproved task type cannot be queued."""

def _now():
    return dt.datetime.now(dt.timezone.utc).timestamp()

def trade_report(payload):
    """Summarize CLOSED DEMO trades, without placing orders or estimating edge."""
    if payload.get('mode') != 'DEMO':
        raise ValueError('Only DEMO reports are accepted')
    rows = payload.get('closed_trades')
    if not isinstance(rows, list) or len(rows) > MAX_TRADES:
        raise ValueError('closed_trades must be a list of at most 100000 rows')
    values = []
    for row in rows:
        if not isinstance(row, dict) or type(row.get('net_pnl_eur')) not in (float, int):
            raise ValueError('Every closed trade needs numeric net_pnl_eur')
        x = float(row['net_pnl_eur'])
        if not math.isfinite(x):
            raise ValueError('Trade P/L must be finite')
        values.append(x)
    equity = peak = max_drawdown = 0.0
    for x in values:
        equity += x
        peak = max(peak, equity)
        max_drawdown = max(max_drawdown, peak - equity)
    wins = sum(x > 0 for x in values)
    losses = sum(x < 0 for x in values)
    return {
        'mode': 'DEMO', 'count': len(values), 'wins': wins,
        'losses': losses, 'breakeven': len(values) - wins - losses,
        'win_rate_pct': round(100 * wins / len(values), 2) if values else None,
        'net_pnl_eur': round(sum(values), 2),
        'max_closed_trade_drawdown_eur': round(max_drawdown, 2),
        'note': 'Based only on supplied closed-trade P/L; not independently broker-verified.'
    }

def run_handler(kind, payload):
    if kind == 'heartbeat':
        return {'ok': True, 'agent': 'lana_core', 'checks': 'local_queue_only'}
    if kind == 'trade_report':
        return trade_report(payload)
    raise UnsafeTaskError('Task type is not registered')

class TaskQueue:
    """SQLite queue with idempotent enqueue and single-worker atomic claims."""
    def __init__(self, db_path):
        self.db_path = str(Path(db_path))
        self._init_db()

    def _connect(self):
        conn = sqlite3.connect(self.db_path, timeout=10)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self):
        with self._connect() as con:
            con.execute('PRAGMA journal_mode=WAL')
            con.execute('''CREATE TABLE IF NOT EXISTS tasks (
                task_id TEXT PRIMARY KEY, idempotency_key TEXT UNIQUE,
                kind TEXT NOT NULL, payload TEXT NOT NULL,
                status TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0,
                lease_until REAL, result TEXT, error TEXT,
                created_at REAL NOT NULL, updated_at REAL NOT NULL
            )''')
            con.execute('''CREATE TABLE IF NOT EXISTS events (
                event_id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id TEXT NOT NULL, event TEXT NOT NULL,
                details TEXT NOT NULL, at REAL NOT NULL
            )''')

    def enqueue(self, kind, payload, idempotency_key=None):
        if kind not in ALLOWED_TASKS:
            raise UnsafeTaskError(f'Task {kind!r} is not allowed; no external actions are enabled')
        if not isinstance(payload, dict):
            raise ValueError('Task payload must be a JSON object')
        data = json.dumps(payload, sort_keys=True, allow_nan=False)
        task_id = str(uuid.uuid4())
        now = _now()
        with self._connect() as con:
            con.execute('BEGIN IMMEDIATE')
            if idempotency_key is not None:
                prior = con.execute('SELECT task_id FROM tasks WHERE idempotency_key=?',
                                    (idempotency_key,)).fetchone()
                if prior:
                    return prior['task_id']
            con.execute('''INSERT INTO tasks (task_id,idempotency_key,kind,payload,status,created_at,updated_at)
                           VALUES (?,?,?,?,?,?,?)''', (task_id,idempotency_key,kind,data,'pending',now,now))
            con.execute('INSERT INTO events(task_id,event,details,at) VALUES (?,?,?,?)',
                        (task_id,'queued','{}',now))
        return task_id

    def run_one(self, lease_seconds=120):
        if lease_seconds <= 0:
            raise ValueError('Lease must be positive')
        now = _now()
        with self._connect() as con:
            con.execute('BEGIN IMMEDIATE')
            task = con.execute('''SELECT * FROM tasks WHERE status='pending'
                OR (status='running' AND lease_until < ?)
                ORDER BY created_at, task_id LIMIT 1''', (now,)).fetchone()
            if task is None:
                return None
            task_id = task['task_id']
            con.execute('''UPDATE tasks SET status='running', attempts=attempts+1,
                lease_until=?, updated_at=? WHERE task_id=?''',
                (now+lease_seconds,now,task_id))
            con.execute('INSERT INTO events(task_id,event,details,at) VALUES (?,?,?,?)',
                        (task_id,'claimed',json.dumps({'attempt':task['attempts']+1}),now))
        # Never execute arbitrary code supplied in a task payload.
        try:
            result = run_handler(task['kind'], json.loads(task['payload']))
            status, error = 'completed', None
        except (ValueError, TypeError, UnsafeTaskError) as exc:
            result, status, error = None, 'failed', f'{type(exc).__name__}: {exc}'
        # Optimistic guard: a recovered claim cannot overwrite another worker's result.
        with self._connect() as con:
            con.execute('BEGIN IMMEDIATE')
            row = con.execute('SELECT status,lease_until FROM tasks WHERE task_id=?',
                              (task_id,)).fetchone()
            if row['status'] != 'running' or row['lease_until'] != now + lease_seconds:
                return task_id
            timestamp = _now()
            con.execute('''UPDATE tasks SET status=?, result=?, error=?, lease_until=NULL,
                updated_at=? WHERE task_id=?''',
                (status,json.dumps(result) if result is not None else None,error,timestamp,task_id))
            con.execute('INSERT INTO events(task_id,event,details,at) VALUES (?,?,?,?)',
                        (task_id,status,json.dumps({'error':error} if error else result),timestamp))
        return task_id

    def get(self, task_id):
        with self._connect() as con:
            row=con.execute('SELECT * FROM tasks WHERE task_id=?',(task_id,)).fetchone()
            if row is None: return None
            info=dict(row)
            for field in ('payload','result'):
                info[field] = json.loads(info[field]) if info[field] is not None else None
            return info

    def events(self, task_id):
        with self._connect() as con:
            return [dict(r) for r in con.execute('SELECT * FROM events WHERE task_id=? ORDER BY event_id',(task_id,))]
