import json
import re
import time
from collections.abc import Callable
from typing import Any, Type, TypeVar

from pydantic import BaseModel, ValidationError

T = TypeVar("T", bound=BaseModel)

PayloadNormalizer = Callable[[dict[str, Any]], dict[str, Any]]


def parse_json_text(raw: str) -> dict[str, Any]:
    text = (raw or "").strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    return json.loads(text)


def validate_model(
    raw: str,
    model: Type[T],
    *,
    normalizer: PayloadNormalizer | None = None,
) -> tuple[T | None, str | None]:
    try:
        data = parse_json_text(raw)
        if normalizer is not None:
            data = normalizer(data)
        return model.model_validate(data), None
    except (json.JSONDecodeError, ValidationError) as e:
        return None, str(e)


def validate_model_with_normalize(
    raw: str,
    model: Type[T],
    normalizer: PayloadNormalizer | None = None,
) -> tuple[T | None, str | None, int]:
    start = time.time()
    validated, err = validate_model(raw, model, normalizer=normalizer)
    cost_ms = int((time.time() - start) * 1000)
    return validated, err, cost_ms
