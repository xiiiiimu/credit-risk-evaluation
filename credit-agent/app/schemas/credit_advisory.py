from pydantic import BaseModel, ConfigDict, Field


class CreditAdvisorySchema(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    suggested_amount: float = Field(
        alias="suggestedAmount",
        ge=0.0,
        description="建议授信额度",
    )
    suggested_rate: float = Field(
        alias="suggestedRate",
        ge=0.0,
        le=1.0,
        description="建议利率",
    )
    suggested_term: int = Field(
        alias="suggestedTerm",
        ge=1,
        le=360,
        description="建议期限（月）",
    )
    confidence: float = Field(
        default=0.7,
        ge=0.0,
        le=1.0,
        description="建议置信度",
    )
    summary: str = Field(default="", description="授信建议摘要")

    def to_legacy_json(self) -> dict:
        return {
            "suggestAmount": self.suggested_amount,
            "suggestRate": self.suggested_rate,
            "suggestTerm": self.suggested_term,
            "confidence": self.confidence,
            "summary": self.summary,
            "rationale": self.summary,
        }
