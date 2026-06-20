from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class CreditAssessmentSchema(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    credit_level: Literal["LOW", "MEDIUM", "HIGH"] = Field(
        alias="creditLevel",
        description="综合信用等级",
    )
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
