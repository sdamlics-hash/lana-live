import json
import threading
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from lana_core.api import create_server
from lana_core.engine import TaskQueue
from lana_core.team import TeamBusy, TeamService


class TeamTests(unittest.TestCase):
    def test_one_hundred_review_cases(self):
        def broken(_):
            raise RuntimeError("private provider diagnostic")

        states = [
            (None, "not_configured"),
            (broken, "provider_error"),
            (lambda _: "", "invalid_response"),
            (lambda _: "a" * 4001, "invalid_response"),
            (lambda _: "предложение A", "answered"),
        ]
        tasks = ["Как ускорить распознавание?", "Как сравнить ответы?", "Составь план.", "Объясни паузу."]
        count = 0
        for left, left_state in states:
            for right, right_state in states:
                adapters = {key: fn for key, fn in (("openai", left), ("gemini", right)) if fn}
                team = TeamService(adapters)
                try:
                    for task in tasks:
                        with self.subTest(case=count + 1, task=task, left=left_state, right=right_state):
                            result = team.dispatch(task)
                            self.assertEqual(result["providers"]["openai"]["state"], left_state)
                            self.assertEqual(result["providers"]["gemini"]["state"], right_state)
                            expected = "same_text" if left_state == right_state == "answered" else "insufficient_answers"
                            self.assertEqual(result["comparison"], expected)
                            self.assertNotIn("private provider diagnostic", repr(result))
                            count += 1
                finally:
                    team.close()
        self.assertEqual(count, 100)

    def test_parallel_execution_preserves_independent_answers(self):
        both_started = threading.Barrier(2)

        def reply(label):
            def run(task):
                both_started.wait(timeout=2)
                return f"{label}: {task}"
            return run

        team = TeamService({"openai": reply("A"), "gemini": reply("B")}, timeout=3)
        self.addCleanup(team.close)
        result = team.dispatch("Проверь предложенный план")
        self.assertEqual(result["comparison"], "different_text")
        self.assertEqual(result["providers"]["openai"]["answer"], "A: Проверь предложенный план")
        self.assertEqual(result["providers"]["gemini"]["answer"], "B: Проверь предложенный план")

    def test_timeout_keeps_success_and_bounds_outstanding_calls(self):
        release = threading.Event()

        def slow(_):
            release.wait(3)
            return "поздний ответ"

        team = TeamService({"openai": lambda _: "готово", "gemini": slow}, timeout=.2)
        self.addCleanup(team.close)
        try:
            result = team.dispatch("Проверь план")
            self.assertEqual(result["providers"]["openai"]["state"], "answered")
            self.assertEqual(result["providers"]["gemini"]["state"], "timed_out")
            with self.assertRaises(TeamBusy):
                team.dispatch("Следующий план")
        finally:
            release.set()

    def test_http_auth_and_explicit_consent_before_transport(self):
        token = "t" * 40
        calls = []

        def adapter(task):
            calls.append(task)
            return "готово"

        with TemporaryDirectory() as directory:
            team = TeamService({"openai": adapter, "gemini": adapter})
            self.addCleanup(team.close)
            server = create_server(TaskQueue(Path(directory) / "test.sqlite"), token, port=0, team_service=team)
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                url = f"http://127.0.0.1:{server.server_port}/v1/team/compare"

                def post(body, authorize=True):
                    headers = {"Content-Type": "application/json"}
                    if authorize:
                        headers["Authorization"] = f"Bearer {token}"
                    request = Request(url, json.dumps(body).encode(), headers)
                    try:
                        with urlopen(request, timeout=3) as response:
                            return response.status, json.load(response)
                    except HTTPError as error:
                        with error:
                            return error.code, json.load(error)

                body = {"task": "Проверь план", "send_to_providers": True}
                self.assertEqual(post(body, False)[0], 401)
                self.assertEqual(post(dict(body, send_to_providers=1))[0], 403)
                self.assertEqual(post(dict(body, context_notes=["не отправлять"]))[0], 400)
                self.assertEqual(calls, [])
                code, result = post(body)
                self.assertEqual(code, 200)
                self.assertEqual(result["comparison"], "same_text")
                self.assertEqual(calls, ["Проверь план", "Проверь план"])
            finally:
                server.shutdown()
                server.server_close()
                worker.join(timeout=3)
