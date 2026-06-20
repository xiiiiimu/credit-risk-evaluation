import logging
import time
from collections.abc import Callable
from dataclasses import dataclass
from typing import TYPE_CHECKING, Any, TypeVar

from langchain_core.language_models.chat_models import BaseChatModel
from pydantic import BaseModel

from app.nodes.common.llm_json import PayloadNormalizer, validate_model, validate_model_with_normalize
from app.nodes.common.llm_repair import repair_once

if TYPE_CHECKING:
    from app.clients.spring_tool_client import SpringToolClient

logger = logging.getLogger("credit.agent.perf")

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
    normalize_cost_ms: int = 0
    repair_cost_ms: int = 0


def _perf_log(
    workflow_id: str | None,
    node: str | None,
    stage: str,
    cost_ms: int,
    **extra: Any,
) -> None:
    suffix = " ".join(f"{key}={value}" for key, value in extra.items())
    logger.info(
        "[PERF][agent] workflowId=%s node=%s stage=%s cost=%sms%s",
        workflow_id or "-",
        node or "-",
        stage,
        cost_ms,
        f" {suffix}" if suffix else "",
    )


def validate_or_repair(
    raw_text: str,
    schema_cls: type[T],
    llm: BaseChatModel,
    *,
    tool_client: "SpringToolClient | None" = None,
    trace_id: str | None = None,
    normalizer: PayloadNormalizer | None = None,
    workflow_id: str | None = None,
    node_name: str | None = None,
) -> ValidationOutcome:
    validated, err, normalize_cost_ms = validate_model_with_normalize(
        raw_text,
        schema_cls,
        normalizer=normalizer,
    )
    if workflow_id or node_name:
        _perf_log(workflow_id, node_name, "schemaNormalize", normalize_cost_ms)

    if validated is not None:
        return ValidationOutcome(
            model=validated,
            validation_error=None,
            repair_triggered=False,
            repair_success=False,
            degraded=False,
            normalize_cost_ms=normalize_cost_ms,
        )

    repair_start = time.time()
    repaired, repair_err = repair_once(
        llm,
        raw_text,
        schema_cls,
        err or "invalid",
        tool_client=tool_client,
        trace_id=trace_id,
        normalizer=normalizer,
    )
    repair_cost_ms = int((time.time() - repair_start) * 1000)
    if workflow_id or node_name:
        _perf_log(workflow_id, node_name, "jsonRepair", repair_cost_ms)

    if repaired is not None:
        return ValidationOutcome(
            model=repaired,
            validation_error=err,
            repair_triggered=True,
            repair_success=True,
            degraded=False,
            normalize_cost_ms=normalize_cost_ms,
            repair_cost_ms=repair_cost_ms,
        )

    return ValidationOutcome(
        model=None,
        validation_error=repair_err or err,
        repair_triggered=True,
        repair_success=False,
        degraded=True,
        normalize_cost_ms=normalize_cost_ms,
        repair_cost_ms=repair_cost_ms,
    )


def validate_or_repair_strict(
    raw_text: str,
    schema_cls: type[T],
    llm: BaseChatModel,
    *,
    tool_client: "SpringToolClient | None" = None,
    trace_id: str | None = None,
    normalizer: PayloadNormalizer | None = None,
    workflow_id: str | None = None,
    node_name: str | None = None,
) -> ValidationOutcome:
    outcome = validate_or_repair(
        raw_text,
        schema_cls,
        llm,
        tool_client=tool_client,
        trace_id=trace_id,
        normalizer=normalizer,
        workflow_id=workflow_id,
        node_name=node_name,
    )
    if outcome.degraded or outcome.model is None:
        raise SchemaValidationError(
            outcome.validation_error or "LLM output failed schema validation",
            outcome,
        )
    return outcome
