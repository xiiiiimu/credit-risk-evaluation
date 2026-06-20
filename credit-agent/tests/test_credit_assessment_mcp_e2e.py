import json
from types import SimpleNamespace

from app.clients.mcp_credit_client import McpCreditClient
from app.config import Settings
from app.nodes.credit_ops import credit_assessment_agent as credit_assessment_module


class FakeSpringToolClient:
    def __init__(self) -> None:
        self.calls: list[tuple[str, dict]] = []

    def invoke(self, tool: str, args: dict, trace_id: str | None = None, **_: object) -> dict:
        self.calls.append((tool, dict(args)))
        return {}


class FakeAssessmentModel:
    def __init__(self) -> None:
        self.credit_eligible = True
        self.confidence = 0.91
        self.income_debt_ratio = 0.31

    def to_legacy_json(self) -> dict:
        return {
            "creditLevel": "LOW",
            "creditScore": 712,
            "incomeDebtRatio": 0.31,
            "riskFactors": [],
            "confidence": 0.91,
            "summary": "base assessment",
            "eligible": True,
            "debtRatio": 0.31,
            "creditScoreEst": 712,
            "rationale": "base assessment",
        }

    def model_dump(self, by_alias: bool = True) -> dict:
        return self.to_legacy_json()


class FakeLLM:
    def invoke(self, _: list[object]) -> SimpleNamespace:
        return SimpleNamespace(content=json.dumps({"summary": "base assessment"}, ensure_ascii=False))


def _fake_validate_or_repair(*_: object, **__: object) -> SimpleNamespace:
    return SimpleNamespace(
        model=FakeAssessmentModel(),
        degraded=False,
        validation_error=None,
        repair_triggered=False,
        repair_success=False,
    )


def _build_settings() -> Settings:
    return Settings(
        mcp_transport="inprocess",
        openai_api_key="test-key",
        openai_base_url="http://example.com",
        openai_chat_model="test-model",
    )


def _build_state(**overrides: object) -> dict:
    state = {
        "user_id": 12,
        "product_id": 1001,
        "application_id": 2001,
        "task_id": 3001,
        "workflow_id": "wf-test",
        "trace_id": "trace-test",
        "apply_amount": 80000.0,
        "apply_term": 12,
        "content": "stable income application content for e2e verification",
        "user_memory": {"monthlyIncome": 18000.0, "employmentType": "SALARIED"},
        "product_info": {"maxAmount": 200000, "interestRate": 0.065},
    }
    state.update(overrides)
    return state


def _run_agent(monkeypatch, **state_overrides: object) -> tuple[dict, FakeSpringToolClient]:
    monkeypatch.setattr(credit_assessment_module, "chat_llm", lambda settings: FakeLLM())
    monkeypatch.setattr(credit_assessment_module, "validate_or_repair", _fake_validate_or_repair)
    settings = _build_settings()
    tool_client = FakeSpringToolClient()
    mcp_client = McpCreditClient(settings)
    result = credit_assessment_module.credit_assessment_agent(
        _build_state(**state_overrides),
        settings=settings,
        tool_client=tool_client,
        mcp_client=mcp_client,
    )
    return result, tool_client


def test_court_enforcement_hit_case(monkeypatch):
    result, _ = _run_agent(monkeypatch, user_id=22)

    assert result["court_enforcement_json"]["dishonestyHit"] is True
    assert result["credit_assessment_json"]["creditLevel"] == "HIGH"
    assert result["agent_vote_credit_assessment"] == "SUGGEST_REJECT"
    assert result["credit_eligible"] is False


def test_income_verification_failure_case(monkeypatch):
    result, _ = _run_agent(
        monkeypatch,
        user_id=13,
        apply_amount=300000.0,
        user_memory={"monthlyIncome": 2800.0, "employmentType": "CONTRACTOR"},
    )

    assert result["income_stability_json"]["incomeVerified"] is False
    assert result["credit_assessment_json"]["creditLevel"] in {"MEDIUM", "HIGH"}
    assert result["agent_vote_credit_assessment"] == "SUGGEST_MANUAL"


def test_three_mcp_tools_called_and_evidence_refs_are_valid(monkeypatch):
    result, tool_client = _run_agent(monkeypatch, user_id=12)

    assert result["credit_bureau_json"]
    assert result["court_enforcement_json"] is not None
    assert result["income_stability_json"]

    evidence_registry = result["evidence_registry"]
    evidence_refs = result["credit_assessment_json"]["evidenceRefs"]
    assert evidence_registry
    assert set(evidence_refs).issubset(set(evidence_registry.keys()))
    assert {
        "mcp_credit_score",
        "mcp_credit_overdue_count_24m",
        "mcp_credit_blacklist_hit",
        "mcp_court_enforcement_count",
        "mcp_court_enforcement_amount",
        "mcp_court_dishonesty_hit",
        "mcp_income_verified",
        "mcp_income_stability_level",
        "mcp_estimated_monthly_income",
        "mcp_debt_to_income_ratio",
    }.issubset(set(evidence_registry.keys()))

    trace_calls = [args for tool, args in tool_client.calls if tool == "save_credit_workflow_trace"]
    assert trace_calls
    tool_calls = trace_calls[-1]["toolCalls"]
    assert "query_credit_record(MCP)" in tool_calls
    assert "query_court_enforcement_record(MCP)" in tool_calls
    assert "verify_income_stability(MCP)" in tool_calls
