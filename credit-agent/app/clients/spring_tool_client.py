import logging
import time
from typing import Any

import httpx

from app.audit.recorder import AuditContext, record_audit
from app.config import Settings, get_settings

logger = logging.getLogger(__name__)


class SpringToolClient:
    """调用 Spring Boot 内部 Tool 网关（/internal/tools/invoke）。"""

    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.spring_tool_base_url.rstrip("/")
        self._api_key = settings.internal_api_key
        self._timeout = httpx.Timeout(30.0)
        self._max_attempts = max(1, getattr(settings, "tool_retry_max_attempts", 3))
        self._backoff_ms = max(50, getattr(settings, "tool_retry_backoff_ms", 300))
        self._failures = 0
        self._circuit_open_until = 0.0
        self._circuit_threshold = getattr(settings, "tool_circuit_failure_threshold", 5)
        self._circuit_open_sec = getattr(settings, "tool_circuit_open_sec", 30)
        self._client_kwargs = {"timeout": self._timeout, "trust_env": False}

    def invoke(
        self,
        tool: str,
        args: dict[str, Any],
        trace_id: str | None = None,
        session_id: str | None = None,
        api_key: str | None = None,
        skip_audit: bool = False,
    ) -> dict[str, Any]:
        now = time.time()
        if now < self._circuit_open_until:
            raise RuntimeError("Spring Tool 网关熔断中，请稍后重试")

        headers = {
            "X-Internal-Api-Key": (api_key or self._api_key or get_settings().internal_api_key),
            "Content-Type": "application/json",
        }
        if trace_id:
            headers["X-Trace-Id"] = trace_id
        if session_id:
            headers["X-Session-Id"] = session_id

        payload = {"tool": tool, "args": args}
        url = f"{self._base_url}/internal/tools/invoke"
        last_err: Exception | None = None
        start = time.time()

        for attempt in range(1, self._max_attempts + 1):
            try:
                with httpx.Client(**self._client_kwargs) as client:
                    response = client.post(url, json=payload, headers=headers)
                    if response.status_code >= 400:
                        logger.error(
                            "Spring tool failed status=%s url=%s body=%s",
                            response.status_code,
                            url,
                            response.text[:500],
                        )
                    response.raise_for_status()
                    body = response.json()
                if not body.get("success", False):
                    raise RuntimeError(body.get("errorMsg") or "tool invoke failed")
                self._failures = 0
                data = body.get("data") or {}
                cost_ms = int((time.time() - start) * 1000)
                if not skip_audit and tool not in {
                    "save_audit_log",
                    "get_llm_cache",
                    "set_llm_cache",
                    "get_ocr_cache",
                    "set_ocr_cache",
                }:
                    workflow_id = args.get("workflowId") or args.get("workflow_id")
                    audit = AuditContext(
                        workflow_id=str(workflow_id) if workflow_id else None,
                        trace_id=trace_id,
                        node_name=tool,
                    )
                    record_audit(
                        self,
                        call_type="TOOL",
                        audit=audit,
                        request=args,
                        response=data,
                        cost_time_ms=cost_ms,
                        success=True,
                    )
                return data
            except Exception as e:
                last_err = e
                logger.warning(
                    "Spring tool retry tool=%s attempt=%s/%s err=%s",
                    tool,
                    attempt,
                    self._max_attempts,
                    e,
                )
                if attempt < self._max_attempts:
                    time.sleep(self._backoff_ms * attempt / 1000.0)

        self._failures += 1
        if self._failures >= self._circuit_threshold:
            self._circuit_open_until = time.time() + self._circuit_open_sec
            logger.error("Spring tool circuit opened after %s failures", self._failures)
        if not skip_audit and tool not in {
            "save_audit_log",
            "get_llm_cache",
            "set_llm_cache",
            "get_ocr_cache",
            "set_ocr_cache",
        }:
            workflow_id = args.get("workflowId") or args.get("workflow_id")
            audit = AuditContext(
                workflow_id=str(workflow_id) if workflow_id else None,
                trace_id=trace_id,
                node_name=tool,
            )
            record_audit(
                self,
                call_type="TOOL",
                audit=audit,
                request=args,
                response="",
                cost_time_ms=int((time.time() - start) * 1000),
                success=False,
                error_msg=str(last_err),
            )
        raise last_err or RuntimeError("tool invoke failed")
