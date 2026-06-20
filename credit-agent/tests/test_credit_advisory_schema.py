"""@deprecated credit_advisory 已从主流程移除；保留 Schema 供历史测试兼容。"""

import pytest

from app.schemas.credit_advisory import CreditAdvisorySchema


pytestmark = pytest.mark.skip(reason="credit_advisory deprecated; 额度/利率/期限由 Java Rule Engine 决定")


def test_credit_advisory_schema_normal_json():
    payload = {
        "suggestedAmount": 50000,
        "suggestedRate": 5.5,
        "suggestedTerm": 12,
        "summary": "demo",
        "confidence": 0.9,
    }
    model = CreditAdvisorySchema.model_validate(payload)
    legacy = model.to_legacy_json()
    assert legacy["suggestAmount"] == 50000
