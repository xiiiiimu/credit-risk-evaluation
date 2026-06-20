from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from app.clients.spring_tool_client import SpringToolClient

DEFAULT_PROMPT_VERSION = 0


@dataclass
class AuditContext:
    workflow_id: str | None = None
    trace_id: str | None = None
    node_name: str | None = None
    prompt_code: str | None = None
    prompt_version: int | None = None
    rule_version: str | None = None


def _truncate_text(value: Any, limit: int = 4000) -> str:
    text = value if isinstance(value, str) else str(value)
    if len(text) <= limit:
        return text
    return text[:limit] + "...[truncated]"


def record_audit(
    tool_client: "SpringToolClient | None",
    *,
    call_type: str,
    audit: AuditContext | None,
    request: Any,
    response: Any,
    token_count: int = 0,
    cost_time_ms: int = 0,
    success: bool = True,
    error_msg: str | None = None,
    cache_hit: bool = False,
) -> None:
    if tool_client is None or audit is None or not audit.workflow_id:
        return
    try:
        tool_client.invoke(
            "save_audit_log",
            {
                "workflowId": audit.workflow_id,
                "traceId": audit.trace_id,
                "nodeName": audit.node_name,
                "callType": call_type,
                "promptVersion": audit.prompt_version,
                "ruleVersion": audit.rule_version,
                "request": _truncate_text(request),
                "response": _truncate_text(response),
                "tokenCount": token_count,
                "costTimeMs": cost_time_ms,
                "success": success,
                "cacheHit": cache_hit,
                "errorMsg": error_msg,
            },
            trace_id=audit.trace_id,
            skip_audit=True,
        )
    except Exception:
        pass
