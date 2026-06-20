import json
from typing import Any

from app.clients.spring_tool_client import SpringToolClient
from app.nodes.common.workflow_trace import trace_node
from app.nodes.credit_ops.consensus_arbitrator import build_consensus
from app.nodes.credit_ops.credit_workflow_trace import NodeTimer, trace_credit_node
from app.state.credit_ops_state import CreditOpsState


def consensus_node(state: CreditOpsState, tool_client: SpringToolClient) -> dict[str, Any]:
    wf = state.get("workflow_id")
    tid = state.get("trace_id")
    timer = NodeTimer()
    trace_node(tool_client, wf, tid, "ConsensusNode", "start")

    merged = dict(state)
    merged["agent_votes"] = {
        "document_review": state.get("agent_vote_document_review") or "SUGGEST_MANUAL_REVIEW",
        "credit_assessment": state.get("agent_vote_credit_assessment") or "SUGGEST_MANUAL_REVIEW",
        "anti_fraud": state.get("agent_vote_anti_fraud") or "SUGGEST_MANUAL_REVIEW",
    }

    result = build_consensus(merged)

    user_mem = state.get("user_memory") or {}
    risk = result.get("risk_level") or str(user_mem.get("riskLevel") or "LOW")
    if int(state.get("recent_apply_count_7d") or user_mem.get("recentApplyCount7d") or 0) > 5:
        risk = "HIGH"
        result["agent_conflicts"] = list(result.get("agent_conflicts") or []) + ["HIGH_FREQUENCY_APPLY"]
        result["conflict_detected"] = True
        result["need_manual_review"] = True

    trace_credit_node(tool_client, state, "consensus", timer.elapsed_ms())
    return {
        "agent_conflicts": result.get("agent_conflicts"),
        "conflict_reasons": result.get("conflict_reasons"),
        "min_confidence": result.get("confidence"),
        "risk_level": risk,
        "need_manual_review": result.get("need_manual_review"),
        "conflict_detected": result.get("conflict_detected"),
        "consensus_suggestion": result.get("consensus_suggestion"),
        "agent_suggestion": result.get("consensus_suggestion"),
        "agent_votes": result.get("agent_votes"),
        "key_risk_factors": result.get("key_risk_factors"),
        "risk_summary": result.get("risk_summary"),
        "ai_summary": result.get("risk_summary") or state.get("ai_summary"),
        "consensus_json": json.dumps(result, ensure_ascii=False),
        "ai_analysis_json": json.dumps(
            {
                "documentReview": state.get("document_review_json"),
                "creditAssessment": state.get("credit_assessment_json"),
                "antiFraud": state.get("anti_fraud_json"),
                "consensus": result,
            },
            ensure_ascii=False,
        ),
    }
