import time
from unittest.mock import MagicMock

import pytest

from app.workflow.retry import WorkflowManualReviewRequired, execute_with_retry


def test_execute_with_retry_success_first_attempt():
    result, retry_count, cost_ms = execute_with_retry(
        lambda: {"ok": True},
        workflow_id="wf-1",
        trace_id="trace-1",
        node_name="document_review",
        agent_name="DocumentReviewAgent",
    )
    assert result == {"ok": True}
    assert retry_count == 0
    assert cost_ms >= 0


def test_execute_with_retry_recovers_on_second_attempt(monkeypatch):
    calls = {"count": 0}

    def flaky():
        calls["count"] += 1
        if calls["count"] == 1:
            raise RuntimeError("temporary")
        return {"ok": True}

    sleeps: list[float] = []
    monkeypatch.setattr(time, "sleep", lambda sec: sleeps.append(sec))

    result, retry_count, _ = execute_with_retry(
        flaky,
        workflow_id="wf-2",
        trace_id="trace-2",
        node_name="anti_fraud",
        agent_name="AntiFraudAgent",
    )
    assert result == {"ok": True}
    assert retry_count == 1
    assert sleeps == [2]


def test_execute_with_retry_manual_review_after_max_retries(monkeypatch):
    monkeypatch.setattr(time, "sleep", lambda _sec: None)

    def always_fail():
        raise RuntimeError("always fail")

    with pytest.raises(WorkflowManualReviewRequired) as exc:
        execute_with_retry(
            always_fail,
            workflow_id="wf-3",
            trace_id="trace-3",
            node_name="credit_assessment",
            agent_name="CreditAssessmentAgent",
        )
    assert exc.value.error_code == "NODE_MAX_RETRY"
    assert exc.value.retry_count == 1
