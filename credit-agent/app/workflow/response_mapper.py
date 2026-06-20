from __future__ import annotations

from typing import Any


def state_to_response_dict(state: dict[str, Any], initial: dict[str, Any] | None = None) -> dict[str, Any]:
    initial = initial or {}
    return {
        "workflowId": state.get("workflow_id") or initial.get("workflow_id"),
        "purpose": state.get("purpose") or initial.get("purpose") or "",
        "verifiedDocuments": bool(state.get("verified_documents")),
        "creditEligible": bool(state.get("credit_eligible")),
        "summary": state.get("risk_summary") or state.get("ai_summary") or "",
        "aiAnalysisJson": state.get("ai_analysis_json") or "",
        "agentSuggestion": state.get("agent_suggestion") or "SUGGEST_MANUAL_REVIEW",
        "consensusSuggestion": state.get("consensus_suggestion") or state.get("agent_suggestion") or "SUGGEST_MANUAL_REVIEW",
        "needManualReview": bool(state.get("need_manual_review")),
        "conflictDetected": bool(state.get("conflict_detected")),
        "bureauUnavailable": bool(state.get("bureau_unavailable")),
        "riskLevel": state.get("risk_level") or "LOW",
        "riskScore": state.get("risk_score_preview"),
        "minAgentConfidence": float(state.get("min_confidence") or 0),
        "agentConflicts": list(state.get("agent_conflicts") or []),
        "fraudHitRules": list(state.get("fraud_hit_rules") or []),
        "fraudScore": int(state.get("fraud_score") or 0),
        "bureauCreditScore": state.get("bureau_credit_score"),
        "incomeDebtRatio": state.get("income_debt_ratio"),
        "documentScore": state.get("document_score"),
        "agentVotes": dict(state.get("agent_votes") or {}),
        "consensusJson": state.get("consensus_json") or "",
        "keyRiskFactors": list(state.get("key_risk_factors") or []),
        "riskSummary": state.get("risk_summary") or "",
        "reason": state.get("reason") or "",
        "answer": state.get("answer") or "",
        "degraded": bool(state.get("degraded")),
        "ticketTitle": state.get("ticket_title") or "",
        "ticketDescription": state.get("ticket_description") or "",
        "recentApplyCount7d": int(state.get("recent_apply_count_7d") or 0),
    }


def response_dict_to_state(result: dict[str, Any]) -> dict[str, Any]:
    return {
        "workflow_id": result.get("workflowId"),
        "verified_documents": result.get("verifiedDocuments"),
        "credit_eligible": result.get("creditEligible"),
        "ai_summary": result.get("summary"),
        "ai_analysis_json": result.get("aiAnalysisJson"),
        "agent_suggestion": result.get("agentSuggestion"),
        "consensus_suggestion": result.get("consensusSuggestion"),
        "need_manual_review": result.get("needManualReview"),
        "conflict_detected": result.get("conflictDetected"),
        "bureau_unavailable": result.get("bureauUnavailable"),
        "risk_level": result.get("riskLevel"),
        "risk_score_preview": result.get("riskScore"),
        "min_confidence": result.get("minAgentConfidence"),
        "agent_conflicts": result.get("agentConflicts"),
        "fraud_hit_rules": result.get("fraudHitRules"),
        "fraud_score": result.get("fraudScore"),
        "bureau_credit_score": result.get("bureauCreditScore"),
        "income_debt_ratio": result.get("incomeDebtRatio"),
        "document_score": result.get("documentScore"),
        "agent_votes": result.get("agentVotes"),
        "consensus_json": result.get("consensusJson"),
        "key_risk_factors": result.get("keyRiskFactors"),
        "risk_summary": result.get("riskSummary"),
        "reason": result.get("reason"),
        "answer": result.get("answer"),
        "degraded": result.get("degraded"),
        "ticket_title": result.get("ticketTitle"),
        "ticket_description": result.get("ticketDescription"),
        "recent_apply_count_7d": result.get("recentApplyCount7d"),
    }
