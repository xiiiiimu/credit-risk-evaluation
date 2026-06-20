from app.nodes.common.llm_json import validate_model
from app.nodes.common.schema_validator import validate_or_repair
from app.schemas.document_review import DocumentReviewSchema
from tests.conftest import MockChatModel

VALID_JSON = """
{
  "docComplete": true,
  "missingDocs": [],
  "confidence": 0.92,
  "summary": "资料完整",
  "ticketTitle": "无需补件",
  "ticketDescription": "全部材料已齐"
}
"""


def test_document_review_schema_normal_json():
    model, err = validate_model(VALID_JSON, DocumentReviewSchema)
    assert err is None
    assert model is not None
    assert model.doc_complete is True


def test_document_review_schema_missing_field():
    raw = '{"confidence":0.8,"summary":"x"}'
    model, err = validate_model(raw, DocumentReviewSchema)
    assert model is None
    assert err is not None


def test_document_review_schema_type_error():
    raw = '{"docComplete":"not-bool","confidence":0.8}'
    model, err = validate_model(raw, DocumentReviewSchema)
    assert model is None
    assert err is not None


def test_document_review_validate_or_repair_success_with_repair():
    broken = '{"docComplete":"not-bool","confidence":0.8}'
    llm = MockChatModel([VALID_JSON])
    outcome = validate_or_repair(broken, DocumentReviewSchema, llm)
    assert outcome.repair_success is True
    assert outcome.model is not None
    assert outcome.model.doc_complete is True


def test_document_review_validate_or_repair_failure():
    broken = '{"confidence":0.8}'
    llm = MockChatModel(['{"summary":"still missing docComplete"}'])
    outcome = validate_or_repair(broken, DocumentReviewSchema, llm)
    assert outcome.model is None
    assert outcome.degraded is True
