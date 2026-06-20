from __future__ import annotations

import time
from collections import deque
from threading import Lock, Semaphore


class LlmRateLimitExceeded(RuntimeError):
    pass


class LlmRateLimiter:
    """并发 + 每分钟请求数双限制，超限排队等待。"""

    def __init__(
        self,
        *,
        max_per_minute: int = 60,
        max_concurrent: int = 5,
        max_wait_sec: float = 30.0,
    ) -> None:
        self._max_per_minute = max(1, max_per_minute)
        self._max_concurrent = max(1, max_concurrent)
        self._semaphore = Semaphore(self._max_concurrent)
        self._max_wait_sec = max(0.1, max_wait_sec)
        self._timestamps: deque[float] = deque()
        self._lock = Lock()

    def acquire(self) -> None:
        deadline = time.time() + self._max_wait_sec
        while True:
            with self._lock:
                now = time.time()
                while self._timestamps and self._timestamps[0] < now - 60.0:
                    self._timestamps.popleft()
                minute_ok = len(self._timestamps) < self._max_per_minute
            if minute_ok and self._semaphore.acquire(blocking=False):
                with self._lock:
                    self._timestamps.append(time.time())
                return
            if time.time() >= deadline:
                raise LlmRateLimitExceeded(
                    f"LLM rate limit exceeded (max/min={self._max_per_minute}, "
                    f"max_concurrent={self._max_concurrent})"
                )
            time.sleep(0.05)

    def release(self) -> None:
        self._semaphore.release()


_limiter: LlmRateLimiter | None = None


def get_llm_rate_limiter(
    *,
    max_per_minute: int = 60,
    max_concurrent: int = 5,
    max_wait_sec: float = 30.0,
) -> LlmRateLimiter:
    global _limiter
    if _limiter is None:
        _limiter = LlmRateLimiter(
            max_per_minute=max_per_minute,
            max_concurrent=max_concurrent,
            max_wait_sec=max_wait_sec,
        )
    return _limiter
