from __future__ import annotations

import hashlib
import json
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from app.clients.spring_tool_client import SpringToolClient


def content_md5(content: str) -> str:
    return hashlib.md5((content or "").encode("utf-8")).hexdigest()


def build_llm_input_hash(messages_preview: str, prompt_version: int) -> str:
    raw = f"{prompt_version}:{messages_preview}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def get_llm_cache(
    tool_client: "SpringToolClient",
    prompt_version: int,
    input_hash: str,
    trace_id: str | None = None,
) -> str | None:
    data = tool_client.invoke(
        "get_llm_cache",
        {"promptVersion": prompt_version, "inputHash": input_hash},
        trace_id=trace_id,
        skip_audit=True,
    )
    if data.get("hit"):
        content = data.get("content")
        return str(content) if content is not None else None
    return None


def set_llm_cache(
    tool_client: "SpringToolClient",
    prompt_version: int,
    input_hash: str,
    content: str,
    trace_id: str | None = None,
) -> None:
    tool_client.invoke(
        "set_llm_cache",
        {
            "promptVersion": prompt_version,
            "inputHash": input_hash,
            "content": content,
        },
        trace_id=trace_id,
        skip_audit=True,
    )


def get_ocr_cache(
    tool_client: "SpringToolClient",
    file_md5: str,
    trace_id: str | None = None,
) -> dict[str, Any] | None:
    data = tool_client.invoke(
        "get_ocr_cache",
        {"fileMd5": file_md5},
        trace_id=trace_id,
        skip_audit=True,
    )
    if not data.get("hit"):
        return None
    content = data.get("content")
    if not content:
        return None
    if isinstance(content, dict):
        return content
    return json.loads(str(content))


def set_ocr_cache(
    tool_client: "SpringToolClient",
    file_md5: str,
    payload: dict[str, Any],
    trace_id: str | None = None,
) -> None:
    tool_client.invoke(
        "set_ocr_cache",
        {"fileMd5": file_md5, "content": json.dumps(payload, ensure_ascii=False)},
        trace_id=trace_id,
        skip_audit=True,
    )
