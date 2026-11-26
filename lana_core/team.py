"""Explicit, bounded two-provider task dispatch for the local bridge."""
from concurrent.futures import ThreadPoolExecutor, wait
from threading import BoundedSemaphore, Lock

PROVIDERS = ("openai", "gemini")
MAX_TASK_CHARS = 2000
MAX_ANSWER_CHARS = 4000


class TeamBusy(RuntimeError):
    pass


class TeamService:
    def __init__(self, adapters, *, timeout=30):
        if not 0 < timeout <= 60:
            raise ValueError("invalid team timeout")
        self._adapters = {name: adapters[name] for name in PROVIDERS if name in adapters}
        self._timeout = timeout
        self._pool = ThreadPoolExecutor(max_workers=len(PROVIDERS), thread_name_prefix="lana-team")
        self._slots = BoundedSemaphore(len(PROVIDERS))
        self._reserve_lock = Lock()

    def close(self):
        self._pool.shutdown(wait=False, cancel_futures=True)

    def _run(self, name, task):
        try:
            answer = self._adapters[name](task)
            if not isinstance(answer, str) or not answer.strip() or len(answer) > MAX_ANSWER_CHARS:
                return {"state": "invalid_response"}
            return {"state": "answered", "answer": answer.strip()}
        except Exception:
            return {"state": "provider_error"}

    def dispatch(self, task):
        if not isinstance(task, str) or not task.strip() or len(task) > MAX_TASK_CHARS:
            raise ValueError("invalid team task")
        results = dict.fromkeys(PROVIDERS)
        available = [name for name in PROVIDERS if name in self._adapters]
        reserved = 0
        with self._reserve_lock:
            for _ in available:
                if not self._slots.acquire(blocking=False):
                    for _ in range(reserved):
                        self._slots.release()
                    raise TeamBusy()
                reserved += 1
        futures = {}
        try:
            for name in available:
                future = self._pool.submit(self._run, name, task.strip())
                future.add_done_callback(lambda _: self._slots.release())
                futures[name] = future
        except RuntimeError:
            for _ in range(reserved - len(futures)):
                self._slots.release()
            raise TeamBusy() from None
        completed, _ = wait(futures.values(), timeout=self._timeout)
        for name in PROVIDERS:
            future = futures.get(name)
            if future is None:
                results[name] = {"state": "not_configured"}
            elif future in completed and not future.cancelled():
                results[name] = future.result()
            else:
                results[name] = {"state": "timed_out"}
        answers = [item["answer"] for item in results.values() if item["state"] == "answered"]
        if len(answers) == 2:
            comparison = "same_text" if answers[0] == answers[1] else "different_text"
        else:
            comparison = "insufficient_answers"
        return {"providers": results, "comparison": comparison}
