from typing import Any, TypedDict


class CreditOpsState(TypedDict, total=False):
    user_id: int
    product_id: int
    application_id: int | None
    task_id: int | None
    apply_amount: float
    apply_term: int
    purpose: str
    content: str
    session_id: str | None
    trace_id: str | None
    workflow_id: str | None

    income: float | None
    occupation: str | None
    age: int | None
    contact_info: str | None
    loan_purpose: str | None
    income_description: str | None
    occupation_description: str | None
    additional_description: str | None
    risk_explanation: str | None
    uploaded_documents: list[dict[str, Any]]
    structured_application: dict[str, Any]
    user_narrative: dict[str, Any]
    ocr_documents: list[dict[str, Any]]
    unified_risk_context: dict[str, Any]
    cross_check_hints: list[str]

    user_memory: dict[str, Any]
    credit_history: dict[str, Any]
    product_info: dict[str, Any]
    recent_apply_count_7d: int

    verify_result: dict[str, Any]
    verified_documents: bool
    document_score: float

    document_review_json: dict[str, Any]
    document_confidence: float
    document_review_result: dict[str, Any]
    agent_vote_document_review: str

    credit_bureau_json: dict[str, Any]
    court_enforcement_json: dict[str, Any]
    income_stability_json: dict[str, Any]
    evidence_registry: dict[str, Any]
    credit_assessment_json: dict[str, Any]
    credit_assessment_result: dict[str, Any]
    credit_assessment_confidence: float
    credit_eligible: bool
    bureau_unavailable: bool
    bureau_credit_score: int
    income_debt_ratio: float
    agent_vote_credit_assessment: str

    anti_fraud_json: dict[str, Any]
    anti_fraud_result: dict[str, Any]
    fraud_score: int
    fraud_hit_rules: list[str]
    anti_fraud_confidence: float
    agent_vote_anti_fraud: str

    credit_advisory_json: dict[str, Any]
    credit_advisory_result: dict[str, Any]
    suggest_amount: float
    suggest_rate: float
    suggest_term: int
    agent_vote_credit_advisory: str

    agent_conflicts: list[str]
    min_confidence: float
    risk_level: str
    risk_score_preview: int
    agent_suggestion: str
    consensus_suggestion: str
    conflict_detected: bool
    agent_votes: dict[str, str]
    consensus_json: str
    need_manual_review: bool

    ai_summary: str
    ai_analysis_json: str
    ticket_title: str
    ticket_description: str
    reason: str
    answer: str
    degraded: bool
