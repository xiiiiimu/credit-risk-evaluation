import pytest
from pydantic import ValidationError

from app.nodes.common.llm_json import validate_model
from app.nodes.common.schema_validator import validate_or_repair
from app.schemas.credit_assessment import CreditAssessmentSchema, normalize_credit_assessment_payload
from tests.conftest import MockChatModel

VALID_JSON = """
{
  "creditLevel": "LOW",
  "creditScore": 720,
  "incomeDebtRatio": 0.25,
  "riskFactors": ["stable income"],
  "confidence": 0.9,
  "summary": "信用良好"
}
"""


def test_credit_assessment_schema_normal_json():
    model, err = validate_model(VALID_JSON, CreditAssessmentSchema)
    assert err is None
    assert model is not None
    assert model.credit_level == "LOW"
    assert model.credit_score == 720
    assert model.credit_eligible is True
    legacy = model.to_legacy_json()
    assert legacy["eligible"] is True
    assert legacy["debtRatio"] == 0.25
    assert legacy["creditScore"] == 720
    assert "riskFactors" in legacy


@pytest.mark.parametrize(
    ("raw_level", "expected"),
    [
        ("良好", "LOW"),
        ("低", "LOW"),
        ("低风险", "LOW"),
        ("low", "LOW"),
        ("Low", "LOW"),
        ("中等", "MEDIUM"),
        ("中", "MEDIUM"),
        ("中风险", "MEDIUM"),
        ("medium", "MEDIUM"),
        ("Medium", "MEDIUM"),
        ("高", "HIGH"),
        ("高风险", "HIGH"),
        ("high", "HIGH"),
        ("High", "HIGH"),
    ],
)
def test_credit_assessment_schema_normalizes_chinese_credit_level(raw_level, expected):
    raw = (
        f'{{"creditLevel":"{raw_level}","creditScore":720,'
        '"incomeDebtRatio":0.25,"confidence":0.9,"summary":"x"}'
    )
    model, err = validate_model(
        raw,
        CreditAssessmentSchema,
        normalizer=normalize_credit_assessment_payload,
    )
    assert err is None
    assert model is not None
    assert model.credit_level == expected


def test_credit_assessment_schema_missing_required_field():
    raw = '{"creditLevel":"LOW","confidence":0.8}'
    model, err = validate_model(raw, CreditAssessmentSchema)
    assert model is None
    assert err is not None


def test_credit_assessment_schema_type_error():
    raw = '{"creditLevel":"LOW","creditScore":"bad","incomeDebtRatio":0.3,"confidence":0.8,"summary":"x"}'
    model, err = validate_model(raw, CreditAssessmentSchema)
    assert model is None
    assert err is not None


def test_credit_assessment_credit_eligible_rule():
    high_score = CreditAssessmentSchema.model_validate(
        {
            "creditLevel": "HIGH",
            "creditScore": 750,
            "incomeDebtRatio": 0.2,
            "confidence": 0.9,
            "summary": "x",
        }
    )
    assert high_score.credit_eligible is False

    low_score = CreditAssessmentSchema.model_validate(
        {
            "creditLevel": "LOW",
            "creditScore": 550,
            "incomeDebtRatio": 0.2,
            "confidence": 0.9,
            "summary": "x",
        }
    )
    assert low_score.credit_eligible is False


def test_credit_assessment_validate_or_repair_success_without_repair():
    llm = MockChatModel([])
    outcome = validate_or_repair(VALID_JSON, CreditAssessmentSchema, llm)
    assert outcome.model is not None
    assert outcome.repair_triggered is False
    assert outcome.degraded is False
    assert llm.invoke_calls == 0


def test_credit_assessment_validate_or_repair_success_with_repair():
    broken = '{"creditLevel":"LOW","creditScore":700}'
    fixed = VALID_JSON
    llm = MockChatModel([fixed])
    outcome = validate_or_repair(broken, CreditAssessmentSchema, llm)
    assert outcome.repair_triggered is True
    assert outcome.repair_success is True
    assert outcome.model is not None
    assert outcome.model.credit_score == 720
    assert llm.invoke_calls == 1


def test_credit_assessment_validate_or_repair_failure():
    broken = '{"creditLevel":"LOW"}'
    llm = MockChatModel(['{"still":"broken"}'])
    outcome = validate_or_repair(broken, CreditAssessmentSchema, llm)
    assert outcome.model is None
    assert outcome.repair_triggered is True
    assert outcome.repair_success is False
    assert outcome.degraded is True
