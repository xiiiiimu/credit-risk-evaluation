from dataclasses import dataclass
from typing import TYPE_CHECKING, TypeVar

from langchain_core.language_models.chat_models import BaseChatModel
from pydantic import BaseModel

from app.nodes.common.llm_json import validate_model
from app.nodes.common.llm_repair import repair_once

if TYPE_CHECKING:
    from app.clients.spring_tool_client import SpringToolClient

T = TypeVar("T", bound=BaseModel)


class SchemaValidationError(RuntimeError):
    def __init__(self, message: str, outcome: "ValidationOutcome") -> None:
        super().__init__(message)
        self.outcome = outcome


@dataclass
class ValidationOutcome:
    model: BaseModel | None
    validation_error: str | None
    repair_triggered: bool
    repair_success: bool
    degraded: bool


def validate_or_repair(
    raw_text: str,
    schema_cls: type[T],
    llm: BaseChatModel,
    *,
    tool_client: "SpringToolClient | None" = None,
    trace_id: str | None = None,
) -> ValidationOutcome:
    validated, err = validate_model(raw_text, schema_cls)
    if validated is not None:
        return ValidationOutcome(
            model=validated,
            validation_error=None,
            repair_triggered=False,
            repair_success=False,
            degraded=False,
        )

    repaired, repair_err = repair_once(
        llm,
        raw_text,
        schema_cls,
        err or "invalid",
        tool_client=tool_client,
        trace_id=trace_id,
    )
    if repaired is not None:
        return ValidationOutcome(
            model=repaired,
            validation_error=err,
            repair_triggered=True,
            repair_success=True,
            degraded=False,
        )

    return ValidationOutcome(
        model=None,
        validation_error=repair_err or err,
        repair_triggered=True,
        repair_success=False,
        degraded=True,
    )


def validate_or_repair_strict(
    raw_text: str,
    schema_cls: type[T],
    llm: BaseChatModel,
    *,
    tool_client: "SpringToolClient | None" = None,
    trace_id: str | None = None,
) -> ValidationOutcome:
    outcome = validate_or_repair(
        raw_text,
        schema_cls,
        llm,
        tool_client=tool_client,
        trace_id=trace_id,
    )
    if outcome.degraded or outcome.model is None:
        raise SchemaValidationError(
            outcome.validation_error or "LLM output failed schema validation",
            outcome,
        )
    return outcome
