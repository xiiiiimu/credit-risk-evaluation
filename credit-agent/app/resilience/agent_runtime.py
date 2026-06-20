from __future__ import annotations

from collections.abc import Callable
from typing import Any, TypeVar

from app.config import Settings
from app.resilience.agent_health import AgentHealthRegistry, get_agent_health_registry
from app.resilience.timeout import AgentTimeoutError, run_with_timeout
from app.workflow.retry import WorkflowManualReviewRequired

T = TypeVar("T")


class AgentRuntimeGuard:
    def __init__(self, settings: Settings) -> None:
        self._timeout_sec = settings.agent_node_timeout_sec
        self._health = get_agent_health_registry(
            failure_threshold=settings.agent_circuit_failure_threshold,
            circuit_open_sec=float(settings.agent_circuit_open_sec),
        )

    @property
    def health(self) -> AgentHealthRegistry:
        return self._health

    def ensure_available(self, agent_name: str | None, node_name: str) -> None:
        if not agent_name:
            return
        if not self._health.is_available(agent_name):
            raise WorkflowManualReviewRequired(
                node_name,
                "AGENT_CIRCUIT_OPEN",
                f"agent {agent_name} circuit open",
                0,
            )

    def run_node(
        self,
        fn: Callable[[], T],
        *,
        agent_name: str | None,
        node_name: str,
    ) -> T:
        self.ensure_available(agent_name, node_name)

        def _execute() -> T:
            if agent_name:
                return run_with_timeout(fn, self._timeout_sec)
            return fn()

        try:
            result = _execute()
            if agent_name:
                self._health.record_success(agent_name)
            return result
        except WorkflowManualReviewRequired:
            raise
        except AgentTimeoutError:
            if agent_name:
                self._health.record_failure(agent_name)
            raise
        except Exception:
            if agent_name:
                self._health.record_failure(agent_name)
            raise
