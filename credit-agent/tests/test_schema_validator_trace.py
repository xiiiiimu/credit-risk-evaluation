from app.nodes.credit_ops.credit_workflow_trace import build_tool_calls_with_validation
from app.nodes.common.schema_validator import ValidationOutcome


def test_build_tool_calls_with_validation_merges_metadata():
    outcome = ValidationOutcome(
        model=None,
        validation_error="field required",
        repair_triggered=True,
        repair_success=False,
        degraded=True,
    )
    merged = build_tool_calls_with_validation(["LLM"], outcome, "credit_assessment")
    assert merged[0] == "LLM"
    assert "schemaValidation" in merged[1]
    meta = merged[1]["schemaValidation"]
    assert meta["nodeName"] == "credit_assessment"
    assert meta["repairTriggered"] is True
    assert meta["repairSuccess"] is False


def test_build_tool_calls_skips_when_no_validation_event():
    outcome = ValidationOutcome(
        model=None,
        validation_error=None,
        repair_triggered=False,
        repair_success=False,
        degraded=False,
    )
    merged = build_tool_calls_with_validation(["LLM"], outcome, "credit_assessment")
    assert merged == ["LLM"]
