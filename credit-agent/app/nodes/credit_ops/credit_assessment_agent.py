import hashlib
import json
from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage

from app.audit.recorder import AuditContext
from app.clients.mcp_credit_client import McpCreditClient, McpTimeoutError
from app.clients.spring_tool_client import SpringToolClient
from app.config import Settings
from app.nodes.common.chat_llm import chat_llm, invoke_llm
from app.nodes.common.schema_validator import validate_or_repair_strict
from app.nodes.common.workflow_trace import trace_node
from app.nodes.credit_ops.credit_workflow_trace import NodeTimer, trace_credit_node
from app.prompts.loader import load_prompt_meta
from app.schemas.credit_assessment import CreditAssessmentSchema
from app.state.credit_ops_state import CreditOpsState


def _merge_evidence(
    existing: dict[str, Any] | None,
    source_tool: str,
    evidence_list: list[dict[str, Any]] | None,
) -> dict[str, Any]:
    registry = dict(existing or {})
    for item in evidence_list or []:
        evidence_id = str(item.get("evidenceId") or item.get("evidence_id") or "").strip()
        if not evidence_id:
            continue
        registry[evidence_id] = {
            **item,
            "evidenceId": evidence_id,
            "sourceTool": item.get("sourceTool") or source_tool,
        }
    return registry


def _apply_external_risk_overrides(
    assessment_json: dict[str, Any],
    *,
    court: dict[str, Any],
    income: dict[str, Any],
) -> tuple[dict[str, Any], bool, float, str]:
    adjusted = dict(assessment_json)
    current_level = str(adjusted.get("creditLevel") or "LOW").upper()
    current_confidence = float(adjusted.get("confidence") or 0.7)
    eligible = bool(adjusted.get("eligible"))

    def bump_level(target: str) -> None:
        levels = {"LOW": 1, "MEDIUM": 2, "HIGH": 3}
        nonlocal current_level
        if levels.get(target, 1) > levels.get(current_level, 1):
            current_level = target

    enforcement_count = int(court.get("enforcementCount") or 0)
    dishonesty_hit = bool(court.get("dishonestyHit"))
    income_verified = bool(income.get("incomeVerified"))
    debt_to_income_ratio = float(income.get("debtToIncomeRatio") or adjusted.get("debtRatio") or 0.0)

    vote = "SUGGEST_APPROVE" if eligible and current_confidence >= 0.8 else (
        "SUGGEST_REJECT" if not eligible else "SUGGEST_MANUAL"
    )
    risk_factors = list(adjusted.get("riskFactors") or [])

    if dishonesty_hit or enforcement_count >= 3:
        bump_level("HIGH")
        eligible = False
        vote = "SUGGEST_REJECT" if dishonesty_hit else "SUGGEST_MANUAL"
        risk_factors.append("COURT_ENFORCEMENT_RISK")

    if (not income_verified) or debt_to_income_ratio >= 0.75:
        bump_level("MEDIUM")
        eligible = False
        if vote != "SUGGEST_REJECT":
            vote = "SUGGEST_MANUAL"
        risk_factors.append("INCOME_STABILITY_RISK")

    adjusted["creditLevel"] = current_level
    adjusted["eligible"] = eligible
    adjusted["debtRatio"] = debt_to_income_ratio
    adjusted["incomeDebtRatio"] = debt_to_income_ratio
    adjusted["riskFactors"] = sorted(set(risk_factors))
    adjusted["confidence"] = current_confidence
    return adjusted, eligible, debt_to_income_ratio, vote


def credit_assessment_agent(
    state: CreditOpsState,
    settings: Settings,
    tool_client: SpringToolClient,
    mcp_client: McpCreditClient,
) -> dict[str, Any]:
    wf = state.get("workflow_id")
    tid = state.get("trace_id")
    timer = NodeTimer()
    trace_node(tool_client, wf, tid, "CreditAssessmentAgent", "start")

    content = (state.get("content") or "").lower()
    force_timeout = "mcp_timeout" in content

    id_hash = McpCreditClient.build_id_hash(state["user_id"])
    bureau: dict[str, Any] = {}
    court: dict[str, Any] = {}
    income: dict[str, Any] = {}
    evidence_registry = dict(state.get("evidence_registry") or {})
    bureau_unavailable = False
    mcp_latency_ms: int | None = None
    mcp_error: str | None = None
    mcp_tool_calls = [
        "query_credit_record(MCP)",
        "query_court_enforcement_record(MCP)",
        "verify_income_stability(MCP)",
    ]

    user_memory = state.get("user_memory") or {}
    monthly_income = float(user_memory.get("monthlyIncome") or user_memory.get("monthly_income") or 12000.0)
    employment_type = str(user_memory.get("employmentType") or user_memory.get("employment_type") or "SALARIED")

    if force_timeout:
        bureau_unavailable = True
        mcp_error = "forced_timeout_demo"
    else:
        mcp_timer = NodeTimer()
        try:
            bureau = mcp_client.query_credit_record(
                id_no_hash=id_hash,
                name=f"user_{state['user_id']}",
                query_reason="LOAN_APPROVAL",
                user_id=state["user_id"],
            )
            evidence_registry = _merge_evidence(evidence_registry, "query_credit_record", bureau.get("evidence"))
            court = mcp_client.query_court_enforcement_record(
                id_no_hash=id_hash,
                name=f"user_{state['user_id']}",
                query_reason="LOAN_APPROVAL",
                user_id=state["user_id"],
            )
            evidence_registry = _merge_evidence(
                evidence_registry, "query_court_enforcement_record", court.get("evidence")
            )
            income = mcp_client.verify_income_stability(
                user_id=state["user_id"],
                monthly_income=monthly_income,
                employment_type=employment_type,
                loan_amount=float(state.get("apply_amount") or 0.0),
                query_reason="LOAN_APPROVAL",
            )
            evidence_registry = _merge_evidence(evidence_registry, "verify_income_stability", income.get("evidence"))
            mcp_latency_ms = mcp_timer.elapsed_ms()
        except McpTimeoutError as e:
            bureau_unavailable = True
            mcp_error = str(e)
            mcp_latency_ms = mcp_timer.elapsed_ms()
        except Exception as e:
            bureau_unavailable = True
            mcp_error = str(e)

    request_hash = hashlib.sha256(
        f"{id_hash}:{state.get('workflow_id') or ''}".encode()
    ).hexdigest()
    tool_client.invoke(
        "save_credit_bureau_query_log",
        {
            "userId": state["user_id"],
            "applicationId": None,
            "requestHash": request_hash,
            "resultSummaryJson": json.dumps(bureau, ensure_ascii=False) if bureau else None,
            "mcpTraceId": tid,
            "mcpLatencyMs": mcp_latency_ms,
            "mcpError": mcp_error,
        },
        trace_id=tid,
    )

    if bureau_unavailable:
        trace_credit_node(
            tool_client,
            state,
            "credit_assessment",
            timer.elapsed_ms(),
            status="DEGRADED",
            error_message=mcp_error,
            mcp_latency_ms=mcp_latency_ms,
            tool_calls=mcp_tool_calls,
        )
        return {
            "credit_bureau_json": bureau,
            "court_enforcement_json": court,
            "income_stability_json": income,
            "evidence_registry": evidence_registry,
            "credit_assessment_json": {
                "eligible": False,
                "debtRatio": 0.5,
                "confidence": 0.3,
                "rationale": "credit external MCP services unavailable, manual review required",
            },
            "credit_assessment_result": None,
            "credit_assessment_confidence": 0.3,
            "credit_eligible": False,
            "bureau_unavailable": True,
            "bureau_credit_score": None,
            "income_debt_ratio": 0.5,
            "agent_vote_credit_assessment": "SUGGEST_MANUAL",
            "reason": "credit external MCP services unavailable, manual review required",
            "need_manual_review": True,
        }

    llm = chat_llm(settings)
    prompt, prompt_version = load_prompt_meta(tool_client, "credit_assessment", tid)
    audit = AuditContext(
        workflow_id=wf,
        trace_id=tid,
        node_name="credit_assessment",
        prompt_code="credit_assessment",
        prompt_version=prompt_version,
    )
    system = SystemMessage(content=prompt)
    human = HumanMessage(
        content=json.dumps(
            {
                "bureau": bureau,
                "courtEnforcement": court,
                "incomeStability": income,
                "userMemory": state.get("user_memory"),
                "applyAmount": state.get("apply_amount"),
                "productContext": (state.get("unified_risk_context") or {}).get("productContext")
                    or state.get("product_context"),
            },
            ensure_ascii=False,
        )
    )
    raw = invoke_llm(llm, [system, human], settings, tool_client=tool_client, audit=audit).content
    raw = raw if isinstance(raw, str) else str(raw)

    outcome = validate_or_repair_strict(
        raw, CreditAssessmentSchema, llm, tool_client=tool_client, trace_id=tid
    )
    validated = outcome.model
    assert validated is not None
    assessment_json = validated.to_legacy_json()
    assessment_result = validated.model_dump(by_alias=True)
    confidence = float(validated.confidence)
    degraded = outcome.degraded

    assessment_json, eligible, debt_ratio, vote = _apply_external_risk_overrides(
        assessment_json,
        court=court,
        income=income,
    )
    evidence_refs = list(evidence_registry.keys())
    assessment_json["evidenceRefs"] = evidence_refs
    assessment_json["courtEnforcement"] = court
    assessment_json["incomeStability"] = income

    bureau_score = int(bureau.get("creditScore") or assessment_json.get("creditScore") or 650)
    assessment_result = {
        **(assessment_result or {}),
        "courtEnforcement": court,
        "incomeStability": income,
        "evidenceRefs": evidence_refs,
    }

    trace_credit_node(
        tool_client,
        state,
        "credit_assessment",
        timer.elapsed_ms(),
        mcp_latency_ms=mcp_latency_ms,
        tool_calls=mcp_tool_calls + ["LLM"],
        validation_outcome=outcome,
    )
    return {
        "credit_bureau_json": bureau,
        "court_enforcement_json": court,
        "income_stability_json": income,
        "evidence_registry": evidence_registry,
        "credit_assessment_json": assessment_json,
        "credit_assessment_result": assessment_result,
        "credit_assessment_confidence": confidence,
        "credit_eligible": eligible,
        "bureau_unavailable": False,
        "bureau_credit_score": bureau_score,
        "income_debt_ratio": debt_ratio,
        "degraded": degraded,
        "agent_vote_credit_assessment": vote,
    }
