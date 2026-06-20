from app.nodes.common.llm_json import validate_model
from app.nodes.common.schema_validator import validate_or_repair
from app.schemas.anti_fraud import AntiFraudSchema
from tests.conftest import MockChatModel

VALID_JSON = """
{
  "fraudLevel": "MEDIUM",
  "fraudSignals": ["proxy ip", "device abnormal"],
  "confidence": 0.88,
  "summary": "存在代理IP与设备异常信号"
}
"""


def test_anti_fraud_schema_normal_json():
    model, err = validate_model(VALID_JSON, AntiFraudSchema)
    assert err is None
    assert model is not None
    assert model.fraud_level == "MEDIUM"
    assert len(model.fraud_signals) == 2


def test_anti_fraud_schema_missing_field():
    raw = '{"fraudSignals":["proxy ip"],"confidence":0.5,"summary":"x"}'
    model, err = validate_model(raw, AntiFraudSchema)
    assert model is None
    assert err is not None


def test_anti_fraud_schema_type_error():
    raw = '{"fraudLevel":"LOW","fraudSignals":"not-a-list","confidence":0.5,"summary":"x"}'
    model, err = validate_model(raw, AntiFraudSchema)
    assert model is None
    assert err is not None


def test_anti_fraud_validate_or_repair_success_without_repair():
    llm = MockChatModel([])
    outcome = validate_or_repair(VALID_JSON, AntiFraudSchema, llm)
    assert outcome.model is not None
    assert not outcome.repair_triggered
    assert llm.invoke_calls == 0


def test_anti_fraud_validate_or_repair_success_with_repair():
    broken = '{"fraudLevel":"CRITICAL","fraudSignals":[],"confidence":0.5,"summary":"x"}'
    llm = MockChatModel([VALID_JSON])
    outcome = validate_or_repair(broken, AntiFraudSchema, llm)
    assert outcome.repair_success is True
    assert outcome.model is not None
    assert outcome.model.summary.startswith("存在")


def test_anti_fraud_validate_or_repair_failure():
    broken = '{"fraudLevel":"BAD"}'
    llm = MockChatModel(['{"fraudLevel":123}'])
    outcome = validate_or_repair(broken, AntiFraudSchema, llm)
    assert outcome.model is None
    assert outcome.degraded is True
    assert outcome.repair_success is False
