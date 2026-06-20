"""P7 structured LLM output schemas."""

from app.schemas.anti_fraud import AntiFraudSchema
from app.schemas.credit_advisory import CreditAdvisorySchema
from app.schemas.credit_assessment import CreditAssessmentSchema
from app.schemas.document_review import DocumentReviewSchema

__all__ = [
    "AntiFraudSchema",
    "CreditAdvisorySchema",
    "CreditAssessmentSchema",
    "DocumentReviewSchema",
]
