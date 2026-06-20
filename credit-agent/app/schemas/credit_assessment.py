from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

_CREDIT_LEVEL_ALIASES: dict[str, str] = {
    "LOW": "LOW",
    "MEDIUM": "MEDIUM",
    "HIGH": "HIGH",
    "低": "LOW",
    "良好": "LOW",
    "低风险": "LOW",
    "LOWRISK": "LOW",
    "LOW_RISK": "LOW",
    "中等": "MEDIUM",
    "中": "MEDIUM",
    "中风险": "MEDIUM",
    "MEDIUMRISK": "MEDIUM",
    "MEDIUM_RISK": "MEDIUM",
    "高": "HIGH",
    "高风险": "HIGH",
    "HIGHRISK": "HIGH",
    "HIGH_RISK": "HIGH",
}


def normalize_credit_level(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    direct = _CREDIT_LEVEL_ALIASES.get(text)
    if direct:
        return direct
    upper = text.upper().replace(" ", "").replace("_", "")
    if upper in {"LOW", "MEDIUM", "HIGH"}:
        return upper
    return _CREDIT_LEVEL_ALIASES.get(text.upper()) or _CREDIT_LEVEL_ALIASES.get(upper)


def normalize_credit_assessment_payload(data: dict[str, Any]) -> dict[str, Any]:
    result = dict(data)
    for key in ("creditLevel", "credit_level"):
        if key not in result:
            continue
        normalized = normalize_credit_level(result[key])
        if normalized:
            result[key] = normalized
            if key == "credit_level":
                result["creditLevel"] = normalized
            else:
                result["credit_level"] = normalized
    return result


class CreditAssessmentSchema(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    credit_level: Literal["LOW", "MEDIUM", "HIGH"] = Field(
        alias="creditLevel",
        description="综合信用等级",
    )

    @field_validator("credit_level", mode="before")
    @classmethod
    def normalize_level(cls, value: Any) -> Any:
        normalized = normalize_credit_level(value)
        return normalized if normalized is not None else value
    credit_score: int = Field(
        alias="creditScore",
        ge=300,
        le=900,
        description="评估信用分",
    )
    income_debt_ratio: float = Field(
        alias="incomeDebtRatio",
        ge=0.0,
        le=1.0,
        description="收入负债比",
    )
    risk_factors: list[str] = Field(
        default_factory=list,
        alias="riskFactors",
        description="风险因素列表",
    )
    confidence: float = Field(
        default=0.7,
        ge=0.0,
        le=1.0,
        description="评估置信度",
    )
    summary: str = Field(default="", description="评估摘要")

    def to_legacy_json(self) -> dict:
        eligible = self.credit_score >= 600 and self.credit_level != "HIGH"
        return {
            "creditLevel": self.credit_level,
            "creditScore": self.credit_score,
            "incomeDebtRatio": self.income_debt_ratio,
            "riskFactors": self.risk_factors,
            "confidence": self.confidence,
            "summary": self.summary,
            "eligible": eligible,
            "debtRatio": self.income_debt_ratio,
            "creditScoreEst": self.credit_score,
            "rationale": self.summary,
        }

    @property
    def credit_eligible(self) -> bool:
        return self.credit_score >= 600 and self.credit_level != "HIGH"
