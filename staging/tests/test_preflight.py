import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("lana_preflight", ROOT / "staging/preflight.py")
preflight = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = preflight
spec.loader.exec_module(preflight)


def by_name(checks):
    return dict(checks)


class StagingTests(unittest.TestCase):
    def test_unconfigured_secrets_block_staging(self):
        checks = by_name(preflight.check(ROOT, environment={}))
        self.assertFalse(checks["Secret LANA_API_TOKEN configured (value hidden)"])
        self.assertFalse(checks["Secret OPENAI_API_KEY configured (value hidden)"])
        self.assertTrue(checks["API bound to IPv4 loopback"])
        self.assertTrue(checks["Worker has no network access"])

    def test_staging_with_strong_mock_secrets_checks_prerequisites(self):
        env = {
            "LANA_API_TOKEN": "mock-token-" + "".join(chr(i) for i in range(65, 91)) + "-123456",
            "OPENAI_API_KEY": "fake-local-testing-credential-not-real",
        }
        checks = preflight.check(ROOT, environment=env)
        self.assertTrue(all(flag for _, flag in checks), checks)
        self.assertNotIn(env["LANA_API_TOKEN"], str(checks))
        self.assertNotIn(env["OPENAI_API_KEY"], str(checks))

    def test_repeated_or_placeholder_tokens_rejected(self):
        testcases = [
            {"LANA_API_TOKEN": "a" * 100,
             "OPENAI_API_KEY": "fake-local-testing-credential-not-real"},
            {"LANA_API_TOKEN": "mock-token-" + "".join(chr(i) for i in range(65,91)) + "-123456",
             "OPENAI_API_KEY": "REPLACE_WITH_SERVER_ONLY_OPENAI_API_KEY"},
        ]
        for env in testcases:
            checks = by_name(preflight.check(ROOT, environment=env))
            self.assertFalse(
                checks["Secret LANA_API_TOKEN configured (value hidden)"] and
                checks["Secret OPENAI_API_KEY configured (value hidden)"]
            )

    def test_unit_files_never_listen_publicly(self):
        api = (ROOT / "staging/systemd/lana-core-api.service").read_text()
        worker = (ROOT / "staging/systemd/lana-core-worker.service").read_text()
        self.assertIn("--host 127.0.0.1", api)
        self.assertNotIn("0.0.0.0", api)
        self.assertIn("EnvironmentFile=/etc/lana/lana.env", api)
        self.assertNotIn("EnvironmentFile=", worker)
        self.assertIn("PrivateNetwork=true", worker)
        for unit in [api,worker]:
            self.assertIn("User=lana", unit)
            self.assertIn("Restart=always", unit)
            self.assertIn("NoNewPrivileges=true", unit)
            self.assertIn("ProtectSystem=strict", unit)
            self.assertIn("StateDirectory=lana", unit)
            self.assertNotIn("ExecStart=/bin/sh", unit)

    def test_missing_source_prevents_ready_status(self):
        with tempfile.TemporaryDirectory() as d:
            checks=by_name(preflight.check(Path(d),environment={}))
            self.assertFalse(checks["File: lana_core/api.py"])
            self.assertFalse(checks["File: staging/systemd/lana-core-api.service"])


if __name__ == "__main__":
    unittest.main()
