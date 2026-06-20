from __future__ import annotations

from enum import Enum
from threading import Lock

from app.resilience.circuit_breaker import AgentCircuitBreaker, CircuitState
from app.workflow.constants import AGENT_NODES


class AgentHealthStatus(str, Enum):
    UP = "UP"
    DOWN = "DOWN"
    DEGRADED = "DEGRADED"


class AgentHealthRegistry:
    def __init__(
        self,
        *,
        failure_threshold: int = 5,
        circuit_open_sec: float = 30.0,
    ) -> None:
        self._lock = Lock()
        self._breakers: dict[str, AgentCircuitBreaker] = {}
        self._failure_threshold = failure_threshold
        self._circuit_open_sec = circuit_open_sec
        for agent_name in set(AGENT_NODES.values()):
            self._breakers[agent_name] = AgentCircuitBreaker(
                failure_threshold=failure_threshold,
                open_sec=circuit_open_sec,
            )

    def _breaker(self, agent_name: str) -> AgentCircuitBreaker:
        with self._lock:
            breaker = self._breakers.get(agent_name)
            if breaker is None:
                breaker = AgentCircuitBreaker(
                    failure_threshold=self._failure_threshold,
                    open_sec=self._circuit_open_sec,
                )
                self._breakers[agent_name] = breaker
            return breaker

    def is_available(self, agent_name: str | None) -> bool:
        if not agent_name:
            return True
        return self._breaker(agent_name).allow_request()

    def record_success(self, agent_name: str | None) -> None:
        if agent_name:
            self._breaker(agent_name).on_success()

    def record_failure(self, agent_name: str | None) -> None:
        if agent_name:
            self._breaker(agent_name).on_failure()

    def status_of(self, agent_name: str) -> AgentHealthStatus:
        breaker = self._breaker(agent_name)
        snap = breaker.snapshot()
        state = snap["state"]
        if state == CircuitState.OPEN.value:
            return AgentHealthStatus.DOWN
        if state == CircuitState.HALF_OPEN.value or int(snap["consecutiveFailures"]) > 0:
            return AgentHealthStatus.DEGRADED
        return AgentHealthStatus.UP

    def snapshot(self) -> dict[str, object]:
        agents: dict[str, dict[str, object]] = {}
        overall = AgentHealthStatus.UP
        for agent_name in sorted(self._breakers):
            status = self.status_of(agent_name)
            agents[agent_name] = {
                "status": status.value,
                **self._breakers[agent_name].snapshot(),
            }
            if status == AgentHealthStatus.DOWN:
                overall = AgentHealthStatus.DOWN
            elif status == AgentHealthStatus.DEGRADED and overall != AgentHealthStatus.DOWN:
                overall = AgentHealthStatus.DEGRADED
        return {"serviceStatus": overall.value, "agents": agents}


_registry: AgentHealthRegistry | None = None


def get_agent_health_registry(
    *,
    failure_threshold: int = 5,
    circuit_open_sec: float = 30.0,
) -> AgentHealthRegistry:
    global _registry
    if _registry is None:
        _registry = AgentHealthRegistry(
            failure_threshold=failure_threshold,
            circuit_open_sec=circuit_open_sec,
        )
    return _registry
