"""Multi-Agent 加权共识仲裁，仅聚合 SUGGEST_* 与风险摘要，不做授信方案决策。"""

from typing import Any

WEIGHTS = {
    "anti_fraud": 0.45,
    "credit_assessment": 0.35,
    "document_review": 0.20,
}

SCORE = {
    "SUGGEST_APPROVE": 1.0,
    "SUGGEST_MANUAL": 0.0,
    "SUGGEST_MANUAL_REVIEW": 0.0,
    "SUGGEST_REJECT": -1.0,
}


def derive_vote(agent: str, state: dict[str, Any]) -> str:
    if agent == "document_review":
        if not state.get("verified_documents"):
            return "SUGGEST_REJECT"
        return "SUGGEST_APPROVE" if float(state.get("document_confidence") or 0) >= 0.7 else "SUGGEST_MANUAL_REVIEW"
    if agent == "credit_assessment":
        if state.get("bureau_unavailable"):
            return "SUGGEST_MANUAL_REVIEW"
        if not state.get("credit_eligible"):
            return "SUGGEST_REJECT"
        return "SUGGEST_APPROVE" if float(state.get("credit_assessment_confidence") or 0) >= 0.8 else "SUGGEST_MANUAL_REVIEW"
    if agent == "anti_fraud":
        fs = int(state.get("fraud_score") or 0)
        if fs >= 70:
            return "SUGGEST_REJECT"
        if fs >= 40:
            return "SUGGEST_MANUAL_REVIEW"
        return "SUGGEST_APPROVE"
    return "SUGGEST_MANUAL_REVIEW"


def _key_risk_factors(state: dict[str, Any]) -> list[str]:
    factors: list[str] = []
    ctx = state.get("unified_risk_context") or {}
    for hint in ctx.get("crossCheckHints") or []:
        factors.append(str(hint))
    if not state.get("verified_documents"):
        factors.append("材料校验未通过")
    if state.get("bureau_unavailable"):
        factors.append("征信不可用")
    if int(state.get("fraud_score") or 0) >= 40:
        factors.append("反欺诈分数偏高")
    product_ctx = ctx.get("productContext") or state.get("product_context") or {}
    apply_amount = float(state.get("apply_amount") or 0)
    max_amount = float(product_ctx.get("maxAmount") or 0)
    if max_amount > 0 and apply_amount > max_amount:
        factors.append("申请金额超过产品最大额度")
    return factors


def build_consensus(state: dict[str, Any]) -> dict[str, Any]:
    votes = {agent: derive_vote(agent, state) for agent in WEIGHTS}
    weighted = sum(SCORE.get(votes[a], 0) * WEIGHTS[a] for a in WEIGHTS)
    unique = set(votes.values())
    conflict = len(unique) > 1 and "SUGGEST_APPROVE" in unique and "SUGGEST_REJECT" in unique

    if conflict or state.get("bureau_unavailable"):
        suggestion = "SUGGEST_MANUAL_REVIEW"
    elif weighted >= 0.5:
        suggestion = "SUGGEST_APPROVE"
    elif weighted <= -0.3:
        suggestion = "SUGGEST_REJECT"
    else:
        suggestion = "SUGGEST_MANUAL_REVIEW"

    conflicts: list[str] = list(state.get("agent_conflicts") or [])
    if conflict:
        conflicts.append("WEIGHTED_AGENT_CONFLICT")

    key_factors = _key_risk_factors(state)
    risk_summary_parts = [state.get("ai_summary") or ""]
    if key_factors:
        risk_summary_parts.append("关键风险：" + "；".join(key_factors))
    risk_summary = "\n".join([p for p in risk_summary_parts if p]).strip()

    confidences = [
        float(state.get("document_confidence") or 0),
        float(state.get("credit_assessment_confidence") or 0),
        float(state.get("anti_fraud_confidence") or 0),
    ]
    confidence = min(confidences) if confidences else 0.0

    risk_level = str(state.get("risk_level") or "MEDIUM")
    if suggestion == "SUGGEST_REJECT":
        risk_level = "HIGH"
    elif suggestion == "SUGGEST_MANUAL_REVIEW":
        risk_level = "MEDIUM" if risk_level == "LOW" else risk_level

    return {
        "consensus_suggestion": suggestion,
        "conflict_detected": conflict or bool(state.get("agent_conflicts")),
        "conflict_reasons": conflicts,
        "agent_votes": votes,
        "agent_conflicts": conflicts,
        "agent_suggestion": suggestion,
        "need_manual_review": conflict
        or bool(state.get("bureau_unavailable"))
        or suggestion in ("SUGGEST_MANUAL", "SUGGEST_MANUAL_REVIEW"),
        "key_risk_factors": key_factors,
        "risk_summary": risk_summary,
        "confidence": confidence,
        "risk_level": risk_level,
    }
