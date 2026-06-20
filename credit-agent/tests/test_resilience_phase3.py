import time

import pytest

from app.resilience.agent_health import AgentHealthRegistry, AgentHealthStatus
from app.resilience.circuit_breaker import AgentCircuitBreaker, CircuitState
from app.resilience.llm_rate_limiter import LlmRateLimiter, LlmRateLimitExceeded
from app.resilience.timeout import AgentTimeoutError, run_with_timeout


def test_circuit_opens_after_threshold():
    breaker = AgentCircuitBreaker(failure_threshold=3, open_sec=10)
    for _ in range(3):
        breaker.on_failure()
    assert breaker.state == CircuitState.OPEN
    assert not breaker.allow_request()


def test_circuit_half_open_then_closes_on_success():
    breaker = AgentCircuitBreaker(failure_threshold=2, open_sec=0.01)
    breaker.on_failure()
    breaker.on_failure()
    time.sleep(0.02)
    assert breaker.allow_request()
    breaker.on_success()
    assert breaker.state == CircuitState.CLOSED


def test_agent_health_registry_marks_down_when_open():
    registry = AgentHealthRegistry(failure_threshold=1, circuit_open_sec=30)
    registry.record_failure("DocumentReviewAgent")
    assert registry.status_of("DocumentReviewAgent") == AgentHealthStatus.DOWN
    assert not registry.is_available("DocumentReviewAgent")


def test_run_with_timeout_raises():
    with pytest.raises(AgentTimeoutError):
        run_with_timeout(lambda: time.sleep(0.2), 0.05)


def test_llm_rate_limiter_blocks_when_saturated():
    limiter = LlmRateLimiter(max_per_minute=1, max_concurrent=1, max_wait_sec=0.1)
    limiter.acquire()
    with pytest.raises(LlmRateLimitExceeded):
        limiter.acquire()
    limiter.release()
