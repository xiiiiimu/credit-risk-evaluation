from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class AntiFraudSchema(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    fraud_level: Literal["LOW", "MEDIUM", "HIGH"] = Field(
        alias="fraudLevel",
        description="LLM 解释层欺诈等级（不影响 Tool 打分投票）",
    )
    fraud_signals: list[str] = Field(
        default_factory=list,
        alias="fraudSignals",
        description="LLM 归纳的风险信号",
    )
    confidence: float = Field(
        default=0.86,
        ge=0.0,
        le=1.0,
        description="解释置信度",
    )
    summary: str = Field(default="", description="反欺诈风险摘要")
