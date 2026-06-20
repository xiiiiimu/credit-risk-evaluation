from typing import Any
import logging
import time

import uvicorn
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field

from app.config import Settings, get_settings
from app.workflow.graph_runner import (
    LOCK_MODE_JAVA_OWNED,
    is_java_owned,
    normalize_lock_mode,
    run_credit_workflow,
)
from app.resilience.agent_health import get_agent_health_registry

logger = logging.getLogger(__name__)

app = FastAPI(
    title="credit-agent",
    description="Credit risk LangGraph agent for credit-risk-platform",
    version="1.0.0",
)


class CreditRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    userId: int
    productId: int
    applyAmount: float
    applyTerm: int = 12
    purpose: str = "CONSUMER"
    content: str = ""
    sessionId: str | None = None
    traceId: str | None = None
    workflowId: str | None = None
    applicationId: int | None = None
    taskId: int | None = None
    lockMode: str | None = None
    lockOwner: str | None = None

    income: float | None = None
    occupation: str | None = None
    age: int | None = None
    contactInfo: str | None = None
    loanPurpose: str | None = None
    incomeDescription: str | None = None
    occupationDescription: str | None = None
    additionalDescription: str | None = None
    riskExplanation: str | None = None
    documents: list[dict[str, Any]] = Field(default_factory=list)
    structuredApplication: dict[str, Any] | None = None
    userNarrative: dict[str, Any] | None = None


class CreditAnalysisResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    workflowId: str | None = None
    purpose: str = ""
    verifiedDocuments: bool = False
    creditEligible: bool = False
    summary: str = ""
    aiAnalysisJson: str = ""
    agentSuggestion: str = "SUGGEST_MANUAL"
    consensusSuggestion: str = "SUGGEST_MANUAL"
    needManualReview: bool = True
    conflictDetected: bool = False
    bureauUnavailable: bool = False
    riskLevel: str = "LOW"
    riskScore: int | None = None
    minAgentConfidence: float = 0.0
    agentConflicts: list[str] = Field(default_factory=list)
    fraudHitRules: list[str] = Field(default_factory=list)
    fraudScore: int = 0
    bureauCreditScore: int | None = None
    incomeDebtRatio: float | None = None
    documentScore: float | None = None
    agentVotes: dict[str, str] = Field(default_factory=dict)
    consensusJson: str = ""
    keyRiskFactors: list[str] = Field(default_factory=list)
    riskSummary: str = ""
    reason: str = ""
    answer: str = ""
    degraded: bool = False
    ticketTitle: str = ""
    ticketDescription: str = ""
    recentApplyCount7d: int = 0


def _resolve_settings(header_internal_key: str | None = None) -> Settings:
    base = get_settings()
    if header_internal_key and header_internal_key.strip():
        key = header_internal_key.strip()
        if key != base.internal_api_key:
            return base.model_copy(update={"internal_api_key": key})
    return base


def _resolve_lock_mode(
    body: CreditRequest,
    x_workflow_lock_mode: str | None,
) -> str | None:
    return normalize_lock_mode(x_workflow_lock_mode) or normalize_lock_mode(body.lockMode)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "credit-agent"}


@app.get("/v1/agents/health")
def agents_health() -> dict[str, object]:
    settings = get_settings()
    registry = get_agent_health_registry(
        failure_threshold=settings.agent_circuit_failure_threshold,
        circuit_open_sec=float(settings.agent_circuit_open_sec),
    )
    return {"status": "ok", "service": "credit-agent", **registry.snapshot()}


@app.post("/v1/agents/credit/analyze", response_model=CreditAnalysisResponse)
def analyze_credit(
    body: CreditRequest,
    x_trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
    x_internal_api_key: str | None = Header(default=None, alias="X-Internal-Api-Key"),
    x_workflow_lock_mode: str | None = Header(default=None, alias="X-Workflow-Lock-Mode"),
    x_workflow_lock_owner: str | None = Header(default=None, alias="X-Workflow-Lock-Owner"),
    x_workflow_lock_owner_token: str | None = Header(default=None, alias="X-Workflow-Lock-Owner-Token"),
) -> CreditAnalysisResponse:
    settings = _resolve_settings(x_internal_api_key)
    if not settings.openai_api_key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY not configured")

    trace_id = body.traceId or x_trace_id
    if not body.workflowId:
        raise HTTPException(status_code=400, detail="workflowId is required")

    lock_mode = _resolve_lock_mode(body, x_workflow_lock_mode)
    lock_owner = x_workflow_lock_owner_token or body.lockOwner or x_workflow_lock_owner
    skip_acquire = lock_mode == LOCK_MODE_JAVA_OWNED

    total_start = time.perf_counter()
    logger.info(
        "[PERF][agent] workflowId=%s lockMode=%s skipAcquire=%s stage=analyze.start",
        body.workflowId,
        lock_mode,
        skip_acquire,
    )

    initial: dict[str, Any] = {
        "user_id": body.userId,
        "product_id": body.productId,
        "application_id": body.applicationId,
        "task_id": body.taskId,
        "apply_amount": body.applyAmount,
        "apply_term": body.applyTerm,
        "purpose": body.purpose,
        "content": body.content or "",
        "session_id": body.sessionId,
        "trace_id": trace_id,
        "workflow_id": body.workflowId,
        "lock_mode": lock_mode,
        "lock_owner": lock_owner,
        "income": body.income,
        "occupation": body.occupation,
        "age": body.age,
        "contact_info": body.contactInfo,
        "loan_purpose": body.loanPurpose,
        "income_description": body.incomeDescription,
        "occupation_description": body.occupationDescription,
        "additional_description": body.additionalDescription,
        "risk_explanation": body.riskExplanation,
        "uploaded_documents": body.documents or [],
        "structured_application": body.structuredApplication,
        "user_narrative": body.userNarrative,
    }
    try:
        graph_start = time.perf_counter()
        result = run_credit_workflow(settings, initial)
        graph_cost_ms = int((time.perf_counter() - graph_start) * 1000)
        logger.info(
            "[PERF][agent] workflowId=%s stage=graphRunner cost=%sms",
            body.workflowId,
            graph_cost_ms,
        )
    except RuntimeError as exc:
        if "still running" in str(exc):
            raise HTTPException(status_code=409, detail=str(exc)) from exc
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    finally:
        total_cost_ms = int((time.perf_counter() - total_start) * 1000)
        logger.info(
            "[PERF][agent] workflowId=%s stage=analyze.total cost=%sms",
            body.workflowId,
            total_cost_ms,
        )
    return _to_response(result, body)


def _to_response(result: dict[str, Any], body: CreditRequest) -> CreditAnalysisResponse:
    return CreditAnalysisResponse(
        workflowId=result.get("workflow_id") or body.workflowId,
        purpose=body.purpose or "",
        verifiedDocuments=bool(result.get("verified_documents")),
        creditEligible=bool(result.get("credit_eligible")),
        summary=result.get("ai_summary") or "",
        aiAnalysisJson=result.get("ai_analysis_json") or "",
        agentSuggestion=result.get("agent_suggestion") or "SUGGEST_MANUAL",
        consensusSuggestion=result.get("consensus_suggestion") or result.get("agent_suggestion") or "SUGGEST_MANUAL",
        needManualReview=bool(result.get("need_manual_review")),
        conflictDetected=bool(result.get("conflict_detected")),
        bureauUnavailable=bool(result.get("bureau_unavailable")),
        riskLevel=result.get("risk_level") or "LOW",
        riskScore=result.get("risk_score_preview"),
        minAgentConfidence=float(result.get("min_confidence") or 0),
        agentConflicts=list(result.get("agent_conflicts") or []),
        fraudHitRules=list(result.get("fraud_hit_rules") or []),
        fraudScore=int(result.get("fraud_score") or 0),
        bureauCreditScore=result.get("bureau_credit_score"),
        incomeDebtRatio=result.get("income_debt_ratio"),
        documentScore=result.get("document_score"),
        agentVotes=dict(result.get("agent_votes") or {}),
        consensusJson=result.get("consensus_json") or "",
        keyRiskFactors=list(result.get("key_risk_factors") or []),
        riskSummary=result.get("risk_summary") or "",
        reason=result.get("reason") or "",
        answer=result.get("answer") or "",
        degraded=bool(result.get("degraded")),
        ticketTitle=result.get("ticket_title") or "",
        ticketDescription=result.get("ticket_description") or "",
        recentApplyCount7d=int(result.get("recent_apply_count_7d") or 0),
    )


if __name__ == "__main__":
    s = get_settings()
    uvicorn.run("app.main:app", host=s.agent_host, port=s.agent_port, reload=False)
