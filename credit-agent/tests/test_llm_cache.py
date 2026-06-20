import json

from langchain_core.messages import HumanMessage, SystemMessage

from app.audit.recorder import AuditContext
from app.cache.result_cache import build_llm_input_hash, content_md5
from app.config import Settings
from app.nodes.common.chat_llm import CachedLlmResponse, invoke_llm


class _FakeLlm:
    def invoke(self, messages):  # noqa: ANN001
        class _Reply:
            content = '{"docComplete":true}'
            response_metadata = {"token_usage": {"total_tokens": 10}}

        return _Reply()


class _FakeToolClient:
    def __init__(self) -> None:
        self.llm_store: dict[str, str] = {}
        self.ocr_store: dict[str, str] = {}
        self.audit_calls: list[dict] = []

    def invoke(self, tool: str, args: dict, trace_id: str | None = None, skip_audit: bool = False) -> dict:
        if tool == "get_llm_cache":
            key = f"{args['promptVersion']}:{args['inputHash']}"
            content = self.llm_store.get(key)
            return {"hit": content is not None, "content": content}
        if tool == "set_llm_cache":
            key = f"{args['promptVersion']}:{args['inputHash']}"
            self.llm_store[key] = str(args["content"])
            return {}
        if tool == "save_audit_log":
            self.audit_calls.append(args)
            return {"id": 1}
        return {}


def test_build_llm_input_hash_stable():
    h1 = build_llm_input_hash('{"role":"user"}', 1)
    h2 = build_llm_input_hash('{"role":"user"}', 1)
    assert h1 == h2
    assert h1 != build_llm_input_hash('{"role":"user"}', 2)


def test_content_md5():
    assert content_md5("hello") == content_md5("hello")
    assert content_md5("hello") != content_md5("world")


def test_invoke_llm_uses_cache_on_second_call():
    settings = Settings(cache_enabled=True)
    client = _FakeToolClient()
    messages = [SystemMessage(content="sys"), HumanMessage(content="hi")]
    audit = AuditContext(workflow_id="wf-1", node_name="document_review", prompt_version=1)

    first = invoke_llm(_FakeLlm(), messages, settings, tool_client=client, audit=audit)
    assert first.content == '{"docComplete":true}'
    assert len(client.llm_store) == 1

    second = invoke_llm(_FakeLlm(), messages, settings, tool_client=client, audit=audit)
    assert isinstance(second, CachedLlmResponse)
    assert second.content == '{"docComplete":true}'
    assert any(call.get("cacheHit") is True for call in client.audit_calls)
