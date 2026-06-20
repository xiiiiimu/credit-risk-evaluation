import json
import re
from typing import Any, Type, TypeVar

from pydantic import BaseModel, ValidationError

T = TypeVar("T", bound=BaseModel)


def parse_json_text(raw: str) -> dict[str, Any]:
    text = (raw or "").strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    return json.loads(text)


def validate_model(raw: str, model: Type[T]) -> tuple[T | None, str | None]:
    try:
        data = parse_json_text(raw)
        return model.model_validate(data), None
    except (json.JSONDecodeError, ValidationError) as e:
        return None, str(e)
