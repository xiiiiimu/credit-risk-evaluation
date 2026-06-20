from __future__ import annotations

import time
from dataclasses import dataclass, field
from enum import Enum
from threading import Lock


class CircuitState(str, Enum):
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"


@dataclass
class AgentCircuitBreaker:
    failure_threshold: int = 5
    open_sec: float = 30.0
    state: CircuitState = CircuitState.CLOSED
    consecutive_failures: int = 0
    open_until: float = 0.0
    half_open_probe_allowed: bool = True
    _lock: Lock = field(default_factory=Lock, repr=False)

    def allow_request(self) -> bool:
        with self._lock:
            now = time.time()
            if self.state == CircuitState.OPEN:
                if now >= self.open_until:
                    self.state = CircuitState.HALF_OPEN
                    self.half_open_probe_allowed = True
                else:
                    return False
            if self.state == CircuitState.HALF_OPEN:
                if not self.half_open_probe_allowed:
                    return False
                self.half_open_probe_allowed = False
            return True

    def on_success(self) -> None:
        with self._lock:
            self.consecutive_failures = 0
            self.state = CircuitState.CLOSED
            self.half_open_probe_allowed = True

    def on_failure(self) -> None:
        with self._lock:
            if self.state == CircuitState.HALF_OPEN:
                self.state = CircuitState.OPEN
                self.open_until = time.time() + self.open_sec
                self.consecutive_failures += 1
                return
            self.consecutive_failures += 1
            if self.consecutive_failures >= self.failure_threshold:
                self.state = CircuitState.OPEN
                self.open_until = time.time() + self.open_sec

    def snapshot(self) -> dict[str, object]:
        with self._lock:
            return {
                "state": self.state.value,
                "consecutiveFailures": self.consecutive_failures,
                "openUntil": self.open_until if self.state == CircuitState.OPEN else None,
            }
