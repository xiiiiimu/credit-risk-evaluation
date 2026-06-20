import pytest

from app.nodes.common.schema_validator import SchemaValidationError, validate_or_repair_strict
from app.prompts.loader import DEFAULT_PROMPTS, load_prompt
from app.schemas.document_review import DocumentReviewSchema


class _FakeToolClient:
    def __init__(self, payload: dict | None = None, error: Exception | None = None) -> None:
        self.payload = payload
        self.error = error
        self.calls: list[tuple[str, dict]] = []

    def invoke(self, tool: str, args: dict, trace_id: str | None = None) -> dict:
        self.calls.append((tool, args))
        if self.error:
            raise self.error
        return self.payload or {}


class _FakeLlm:
    def invoke(self, messages):  # noqa: ANN001
        class _Reply:
            content = "not-json"

        return _Reply()


def test_load_prompt_from_platform():
    client = _FakeToolClient({"promptContent": "from-db"})
    assert load_prompt(client, "document_review") == "from-db"
    assert client.calls[0][0] == "get_prompt_config"


def test_load_prompt_fallback_on_error():
    client = _FakeToolClient(error=RuntimeError("down"))
    assert load_prompt(client, "document_review") == DEFAULT_PROMPTS["document_review"]


def test_load_prompt_fallback_without_client():
    assert load_prompt(None, "anti_fraud") == DEFAULT_PROMPTS["anti_fraud"]


def test_validate_or_repair_strict_raises_on_invalid_json():
    with pytest.raises(SchemaValidationError) as exc:
        validate_or_repair_strict("{}", DocumentReviewSchema, _FakeLlm())
    assert exc.value.outcome.degraded is True
