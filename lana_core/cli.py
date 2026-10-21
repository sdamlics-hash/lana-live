"""Command line interface. Example: python -m lana_core.cli --db state.sqlite heartbeat"""
import argparse
import json
import time
from .engine import TaskQueue

def main():
    p = argparse.ArgumentParser(description='LANA offline task queue prototype (no external actions)')
    p.add_argument('--db', default='lana_tasks.sqlite3')
    sub = p.add_subparsers(dest='command', required=True)
    sub.add_parser('heartbeat')
    audit = sub.add_parser('trade-report')
    audit.add_argument('report_json', help='Path to JSON with mode=DEMO and closed_trades')
    sub.add_parser('run-once')
    daemon = sub.add_parser('daemon')
    daemon.add_argument('--interval', type=int, default=10)
    show = sub.add_parser('show')
    show.add_argument('task_id')
    args = p.parse_args()
    queue=TaskQueue(args.db)
    if args.command == 'heartbeat':
        print(queue.enqueue('heartbeat', {}))
    elif args.command == 'trade-report':
        with open(args.report_json, encoding='utf-8') as f:
            payload=json.load(f)
        print(queue.enqueue('trade_report',payload))
    elif args.command == 'run-once':
        print(queue.run_one() or 'no pending jobs')
    elif args.command == 'daemon':
        if args.interval < 1:
            p.error('interval must be at least 1 second')
        try:
            while True:
                queue.run_one()
                time.sleep(args.interval)
        except KeyboardInterrupt:
            print('stopped')
    elif args.command == 'show':
        print(json.dumps({'task':queue.get(args.task_id),'events':queue.events(args.task_id)},indent=2,ensure_ascii=False))

if __name__ == '__main__':
    main()
