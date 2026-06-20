"""Workflow 节点顺序与 Agent 节点定义。"""

NODE_ORDER: list[str] = [
    "load_memory",
    "ocr_preprocess",
    "input_fusion",
    "document_review",
    "document_verify",
    "credit_assessment",
    "anti_fraud",
    "consensus",
    "suggestion_routing",
    "final",
]

AGENT_NODES: dict[str, str] = {
    "document_review": "DocumentReviewAgent",
    "credit_assessment": "CreditAssessmentAgent",
    "anti_fraud": "AntiFraudAgent",
}

MAX_NODE_RETRIES = 3
RETRY_BACKOFF_SEC = (2, 4, 8)

WORKFLOW_STATUS_INIT = "INIT"
WORKFLOW_STATUS_SUCCESS = "SUCCESS"
WORKFLOW_STATUS_FAILED = "FAILED"
WORKFLOW_STATUS_MANUAL_REVIEW = "MANUAL_REVIEW"
WORKFLOW_STATUS_RUNNING = "RUNNING"
