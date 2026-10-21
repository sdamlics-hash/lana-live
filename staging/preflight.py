"""Read-only readiness check for a future, *isolated* LANA staging VM.

Does not connect to OpenAI, touch production systems, install services,
modify files or reveal tokens. Run only on a machine you own/control.
"""
import argparse
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_FILES = (
    ROOT / "lana_core" / "api.py",
    ROOT / "lana_core" / "cli.py",
    ROOT / "lana_core" / "chat.py",
    ROOT / "staging" / "systemd" / "lana-core-api.service",
    ROOT / "staging" / "systemd" / "lana-core-worker.service",
)


def check(source_root=ROOT, environment=None):
    """Return actionable pass/fail checks without exposing any credentials."""
    base = Path(source_root)
    env = os.environ if environment is None else environment
    checks = []
    checks.append(("Python >= 3.10", sys.version_info >= (3, 10)))
    for relative in (
        "lana_core/api.py",
        "lana_core/cli.py",
        "lana_core/chat.py",
        "staging/systemd/lana-core-api.service",
        "staging/systemd/lana-core-worker.service",
    ):
        checks.append((f"File: {relative}", (base / relative).is_file()))
    api_file = base / "staging/systemd/lana-core-api.service"
    worker_file = base / "staging/systemd/lana-core-worker.service"
    if api_file.is_file():
        api = api_file.read_text(encoding="utf-8")
        checks.append(("API bound to IPv4 loopback", "--host 127.0.0.1" in api))
        checks.append(("API restricted as unprivileged service",
                       "User=lana" in api and "NoNewPrivileges=true" in api))
        checks.append(("API configured to restart", "Restart=always" in api))
    if worker_file.is_file():
        worker = worker_file.read_text(encoding="utf-8")
        checks.append(("Worker has no network access", "PrivateNetwork=true" in worker))
        checks.append(("Worker does not load OpenAI secrets", "EnvironmentFile=" not in worker))
        checks.append(("Worker configured to restart", "Restart=always" in worker))
    token = env.get("LANA_API_TOKEN", "")
    api_key = env.get("OPENAI_API_KEY", "")
    token_ok = isinstance(token, str) and len(token) >= 40 and len(set(token)) >= 16
    key_ok = isinstance(api_key, str) and len(api_key.strip()) >= 20 and not api_key.startswith("REPLACE_")
    checks.append(("Secret LANA_API_TOKEN configured (value hidden)", token_ok))
    checks.append(("Secret OPENAI_API_KEY configured (value hidden)", key_ok))
    return checks


def main():
    ap = argparse.ArgumentParser(description="Validate LANA staging prerequisites locally")
    ap.add_argument("--source-root", type=Path, default=ROOT)
    args = ap.parse_args()
    checks = check(args.source_root)
    for title, success in checks:
        print(("[OK] " if success else "[BLOCKED] ") + title)
    if not all(passed for _, passed in checks):
        print("Not ready: staging must remain offline until prerequisites pass.")
        sys.exit(2)
    print("Prerequisites present. This does NOT mean LANA was deployed or live-tested.")


if __name__ == "__main__":
    main()
