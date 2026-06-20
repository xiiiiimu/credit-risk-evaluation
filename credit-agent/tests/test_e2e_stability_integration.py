"""E2E / 灰度 / 稳定性：Python Workflow 集成测试（Mock Tool / LLM）。"""

from unittest.mock import MagicMock, patch

import pytest

from app.prompts.loader import load_prompt_meta
from app.workflow.constants import NODE_ORDER
from app.workflow.retry import WorkflowManualReviewRequired, execute_with_retry


class _FakeToolClient:
    def __init__(self, prompts: dict | None = None) -> None:
        self.prompts = prompts or {}
        self.calls: list[tuple[str, dict]] = []

    def invoke(self, tool: str, args: dict, trace_id: str | None = None, skip_audit: bool = False) -> dict:
        self.calls.append((tool, args))
        if tool == "get_prompt_config":
            code = args.get("promptCode")
            payload = self.prompts.get(code)
            return payload or {}
        if tool == "get_credit_product":
            return {
                "productId": args.get("productId"),
                "productName": "消费信用贷",
                "maxAmount": 200000,
                "requiredMaterials": [{"documentType": "ID_CARD", "required": True}],
            }
        return {}


def test_e2e_workflow_order_coversFullAgentChainWithoutCreditAdvisory():
    assert "credit_advisory" not in NODE_ORDER
    assert NODE_ORDER[-1] == "final"
    assert "consensus" in NODE_ORDER


@patch("app.workflow.graph_runner.WorkflowPersistenceClient")
@patch("app.workflow.graph_runner.SpringToolClient")
@patch("app.workflow.graph_runner.build_node_callables")
def test_e2e_duplicateWorkflowId_skipsAgentExecution(mock_build, _tool, mock_persistence_cls):
    from app.workflow.graph_runner import run_credit_workflow

    persistence = MagicMock()
    mock_persistence_cls.return_value = persistence
    persistence.resolve_idempotent.return_value = {
        "action": "RETURN_RESULT",
        "result": {"agentSuggestion": "SUGGEST_APPROVE", "workflowId": "wf-dup-py"},
    }

    from app.config import Settings

    result = run_credit_workflow(Settings(openai_api_key="k"), {"workflow_id": "wf-dup-py", "trace_id": "t1"})
    assert result.get("agent_suggestion") == "SUGGEST_APPROVE"
    mock_build.assert_not_called()


def test_gray_promptV2_agentLoadsNewVersionFromPlatformTool():
    client = _FakeToolClient(
        {
            "document_review": {
                "promptContent": "Prompt V2: 仅评估材料完整性，禁止输出额度利率期限",
                "version": 2,
            }
        }
    )
    content, version = load_prompt_meta(client, "document_review", "trace-v2")
    assert "Prompt V2" in content
    assert version == 2
    assert client.calls[0][0] == "get_prompt_config"


def test_stability_nodeRetryExhausted_entersManualReview(monkeypatch):
    monkeypatch.setattr("app.workflow.retry.time.sleep", lambda _sec: None)

    with pytest.raises(WorkflowManualReviewRequired) as exc:
        execute_with_retry(
            lambda: (_ for _ in ()).throw(RuntimeError("timeout")),
            workflow_id="wf-retry",
            trace_id="trace-retry",
            node_name="credit_assessment",
            agent_name="CreditAssessmentAgent",
        )
    assert exc.value.error_code == "NODE_MAX_RETRY"


@patch("app.workflow.graph_runner.WorkflowPersistenceClient")
@patch("app.workflow.graph_runner.SpringToolClient")
@patch("app.workflow.graph_runner.build_node_callables")
def test_stability_checkpointResume_startsAfterLastSuccessfulNode(mock_build, _tool, mock_persistence_cls):
    from app.workflow.graph_runner import run_credit_workflow

    persistence = MagicMock()
    mock_persistence_cls.return_value = persistence
    persistence.resolve_idempotent.return_value = {"action": "RUN", "acquired": True}
    persistence.acquire_execution.return_value = {"action": "RUN", "acquired": True}

    executed_nodes: list[str] = []

    def fake_node(state):
        executed_nodes.append("document_verify")
        return {"verified_documents": True, "document_confidence": 0.9}

    mock_build.return_value = {
        "load_memory": lambda s: s,
        "ocr_preprocess": lambda s: s,
        "input_fusion": lambda s: s,
        "document_review": lambda s: s,
        "document_verify": fake_node,
        "credit_assessment": lambda s: s,
        "anti_fraud": lambda s: s,
        "consensus": lambda s: {"consensus_suggestion": "SUGGEST_APPROVE"},
        "suggestion_routing": lambda s: s,
        "final": lambda s: s,
    }

    persistence.load_checkpoint.return_value = {
        "currentNode": "document_review",
        "state": {"workflow_id": "wf-resume", "user_id": 1, "product_id": 1},
        "history": [{"node": "document_review", "status": "SUCCESS"}],
        "retryCount": 0,
    }

    from app.config import Settings

    run_credit_workflow(
        Settings(openai_api_key="k"),
        {"workflow_id": "wf-resume", "trace_id": "t-resume", "user_id": 1, "product_id": 1},
    )
    assert executed_nodes == ["document_verify"]
