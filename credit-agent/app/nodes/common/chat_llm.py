import json
import time
from typing import Any

from langchain_core.messages import BaseMessage
from langchain_openai import ChatOpenAI

from app.audit.recorder import AuditContext, record_audit
from app.cache.result_cache import build_llm_input_hash, get_llm_cache, set_llm_cache
from app.config import Settings
from app.resilience.llm_rate_limiter import get_llm_rate_limiter


class CachedLlmResponse:
    def __init__(self, content: str) -> None:
        self.content = content
        self.response_metadata = {"cache_hit": True, "token_usage": {"total_tokens": 0}}


def chat_llm(settings: Settings, temperature: float = 0.2) -> ChatOpenAI:
    kwargs: dict = {
        "api_key": settings.openai_api_key,
        "model": settings.openai_chat_model,
        "temperature": temperature,
    }
    base_url = (settings.openai_base_url or "").strip()
    if base_url:
        kwargs["base_url"] = base_url
    return ChatOpenAI(**kwargs)


def _extract_token_count(response: Any) -> int:
    meta = getattr(response, "response_metadata", None) or {}
    usage = meta.get("token_usage") or meta.get("usage") or {}
    if isinstance(usage, dict):
        total = usage.get("total_tokens") or usage.get("totalTokens")
        if total is not None:
            return int(total)
        prompt = int(usage.get("prompt_tokens") or usage.get("promptTokens") or 0)
        completion = int(usage.get("completion_tokens") or usage.get("completionTokens") or 0)
        return prompt + completion
    return 0


def _message_preview(messages: list[BaseMessage]) -> str:
    payload = []
    for msg in messages:
        payload.append({"role": getattr(msg, "type", "unknown"), "content": str(getattr(msg, "content", ""))})
    return json.dumps(payload, ensure_ascii=False)


def invoke_llm(
    llm: ChatOpenAI,
    messages: list[BaseMessage],
    settings: Settings,
    *,
    tool_client: Any = None,
    audit: AuditContext | None = None,
) -> Any:
    preview = _message_preview(messages)
    prompt_version = audit.prompt_version if audit and audit.prompt_version is not None else 0
    input_hash = build_llm_input_hash(preview, prompt_version)

    if settings.cache_enabled and tool_client is not None:
        cached_content = get_llm_cache(
            tool_client,
            prompt_version,
            input_hash,
            trace_id=audit.trace_id if audit else None,
        )
        if cached_content is not None:
            record_audit(
                tool_client,
                call_type="LLM",
                audit=audit,
                request=preview,
                response=cached_content,
                token_count=0,
                cost_time_ms=0,
                success=True,
                cache_hit=True,
            )
            return CachedLlmResponse(cached_content)

    limiter = get_llm_rate_limiter(
        max_per_minute=settings.llm_rate_limit_per_minute,
        max_concurrent=settings.llm_rate_limit_max_concurrent,
        max_wait_sec=settings.llm_rate_limit_max_wait_sec,
    )
    start = time.time()
    limiter.acquire()
    try:
        response = llm.invoke(messages)
        cost_ms = int((time.time() - start) * 1000)
        content = response.content if hasattr(response, "content") else str(response)
        if settings.cache_enabled and tool_client is not None:
            set_llm_cache(
                tool_client,
                prompt_version,
                input_hash,
                content if isinstance(content, str) else str(content),
                trace_id=audit.trace_id if audit else None,
            )
        record_audit(
            tool_client,
            call_type="LLM",
            audit=audit,
            request=preview,
            response=content,
            token_count=_extract_token_count(response),
            cost_time_ms=cost_ms,
            success=True,
            cache_hit=False,
        )
        return response
    except Exception as exc:
        cost_ms = int((time.time() - start) * 1000)
        record_audit(
            tool_client,
            call_type="LLM",
            audit=audit,
            request=preview,
            response="",
            token_count=0,
            cost_time_ms=cost_ms,
            success=False,
            error_msg=str(exc),
            cache_hit=False,
        )
        raise
    finally:
        limiter.release()
