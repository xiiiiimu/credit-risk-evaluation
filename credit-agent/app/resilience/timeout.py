from __future__ import annotations

from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeout
from typing import TypeVar

T = TypeVar("T")


class AgentTimeoutError(TimeoutError):
    pass


def run_with_timeout(fn: Callable[[], T], timeout_sec: float) -> T:
    if timeout_sec <= 0:
        return fn()
    with ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(fn)
        try:
            return future.result(timeout=timeout_sec)
        except FuturesTimeout as exc:
            raise AgentTimeoutError(f"agent execution timeout after {timeout_sec}s") from exc
