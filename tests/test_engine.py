import json
import sqlite3
import tempfile
import unittest
from pathlib import Path
from lana_core.engine import TaskQueue, UnsafeTaskError

class QueueTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.db = Path(self.temp.name) / 'tasks.db'
        self.queue=TaskQueue(self.db)
    def tearDown(self): self.temp.cleanup()

    def test_heartbeat_persists_across_restart(self):
        uid=self.queue.enqueue('heartbeat', {})
        self.assertEqual(self.queue.run_one(),uid)
        self.assertEqual(TaskQueue(self.db).get(uid)['status'],'completed')
        self.assertEqual(TaskQueue(self.db).get(uid)['result']['checks'],'local_queue_only')

    def test_idempotency(self):
        first=self.queue.enqueue('heartbeat', {}, 'same-event')
        second=self.queue.enqueue('heartbeat', {}, 'same-event')
        self.assertEqual(first,second)
        self.assertEqual(self.queue.run_one(),first)
        self.assertIsNone(self.queue.run_one())

    def test_refuse_external_write(self):
        for kind in ('send_email','real_trade','payment','run_shell_command'):
            with self.assertRaises(UnsafeTaskError):
                self.queue.enqueue(kind, {})

    def test_trade_report_demonstrates_closed_only(self):
        uid=self.queue.enqueue('trade_report',{'mode':'DEMO','closed_trades':[
            {'net_pnl_eur':10},{'net_pnl_eur':-4},{'net_pnl_eur':-8},
            {'net_pnl_eur':2},{'net_pnl_eur':0}]})
        self.queue.run_one()
        result=self.queue.get(uid)['result']
        self.assertEqual((result['count'],result['wins'],result['losses'],result['breakeven']),(5,2,2,1))
        self.assertEqual(result['win_rate_pct'],40)
        self.assertEqual(result['net_pnl_eur'],0)
        self.assertEqual(result['max_closed_trade_drawdown_eur'],12)

    def test_reject_real_mode(self):
        uid=self.queue.enqueue('trade_report',{'mode':'REAL','closed_trades':[]})
        self.queue.run_one()
        self.assertEqual(self.queue.get(uid)['status'],'failed')

    def test_claim_expiry_recovery(self):
        uid=self.queue.enqueue('heartbeat',{})
        with sqlite3.connect(self.db) as con:
            con.execute("UPDATE tasks SET status='running',lease_until=0 WHERE task_id=?",(uid,))
        self.assertEqual(self.queue.run_one(),uid)
        self.assertEqual(self.queue.get(uid)['attempts'],1)
        self.assertEqual(self.queue.get(uid)['status'],'completed')

    def test_error_is_logged(self):
        uid=self.queue.enqueue('trade_report',{'mode':'DEMO','closed_trades':[{'bad':'not closed'}]})
        self.queue.run_one()
        self.assertEqual(self.queue.get(uid)['status'],'failed')
        self.assertEqual([e['event'] for e in self.queue.events(uid)],['queued','claimed','failed'])

if __name__=='__main__': unittest.main()
