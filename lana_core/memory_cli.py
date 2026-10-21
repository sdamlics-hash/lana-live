"""Operator-only management of LANA's SQLite project memory.

This module is local. It is not exposed through the HTTP API.
Never supply user-specific secrets via command-line arguments or GitHub.
"""
import argparse
import json
from .memory import MemoryStore, ALLOWED_SCOPES


def main(argv=None):
    parser = argparse.ArgumentParser(description="LANA locally curated memory")
    parser.add_argument("--db", required=True, help="Path to private server SQLite DB")
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list")
    set_p = sub.add_parser("set")
    set_p.add_argument("scope", choices=sorted(ALLOWED_SCOPES))
    set_p.add_argument("key")
    set_p.add_argument("value")
    set_p.add_argument("--confirm-approval", action="store_true")
    delete_p = sub.add_parser("delete")
    delete_p.add_argument("scope", choices=sorted(ALLOWED_SCOPES))
    delete_p.add_argument("key")
    delete_p.add_argument("--confirm-approval", action="store_true")
    args = parser.parse_args(argv)
    memory = MemoryStore(args.db)
    if args.cmd == "list":
        print(json.dumps(memory.all(), ensure_ascii=False, indent=2))
    elif args.cmd == "set":
        memory.set(args.scope, args.key, args.value, approved=args.confirm_approval)
        print("approved memory stored locally")
    elif args.cmd == "delete":
        changed = memory.delete(args.scope, args.key, approved=args.confirm_approval)
        print("approved memory deleted" if changed else "key not found")


if __name__ == "__main__":
    main()
