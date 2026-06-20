from unittest.mock import MagicMock, patch

from app.config import Settings
from app.workflow.graph_runner import _resume_start_index


def test_resume_start_index_from_checkpoint():
    assert _resume_start_index({"currentNode": "document_review"}) == 2
    assert _resume_start_index({"currentNode": "final"}) == 9
    assert _resume_start_index(None) == 0


@patch("app.workflow.graph_runner.WorkflowPersistenceClient")
@patch("app.workflow.graph_runner.SpringToolClient")
@patch("app.workflow.graph_runner.build_node_callables")
def test_run_credit_workflow_returns_cached_result(mock_build, _tool_client, mock_persistence_cls):
    from app.workflow.graph_runner import run_credit_workflow

    persistence = MagicMock()
    mock_persistence_cls.return_value = persistence
    persistence.resolve_idempotent.return_value = {
        "action": "RETURN_RESULT",
        "result": {"agentSuggestion": "SUGGEST_APPROVE", "workflowId": "wf-cache"},
    }

    settings = Settings(openai_api_key="k")
    result = run_credit_workflow(settings, {"workflow_id": "wf-cache", "trace_id": "t1"})
    assert result.get("agent_suggestion") == "SUGGEST_APPROVE"
    mock_build.assert_not_called()
    persistence.start_workflow.assert_not_called()
