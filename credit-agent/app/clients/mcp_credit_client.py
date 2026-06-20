from __future__ import annotations

import asyncio
import hashlib
import json
import logging
import sys
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FuturesTimeout
from pathlib import Path
from typing import Any

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

from app.config import Settings, get_settings
from app.mcp_server.credit_mcp_server import call_tool as call_tool_inprocess

logger = logging.getLogger(__name__)

_PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent


class McpTimeoutError(Exception):
    pass


class McpCreditClient:
    """
    征信 MCP 客户端。
    - stdio（默认）：通过 MCP 协议启动独立 Server 子进程
    - inprocess：进程内直调（仅用于本地调试/单测）
    """

    def __init__(self, settings: Settings | None = None, timeout_sec: float | None = None) -> None:
        settings = settings or get_settings()
        self._transport = (settings.mcp_transport or "stdio").strip().lower()
        self._timeout_sec = timeout_sec if timeout_sec is not None else settings.mcp_timeout_sec
        self._server_command = settings.mcp_server_command or sys.executable
        self._server_args = settings.mcp_server_args or ["-m", "app.mcp_server"]
        self._server_cwd = settings.mcp_server_cwd or str(_PROJECT_ROOT)
        self._executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="mcp-credit")

    def call_tool_sync(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        if self._transport == "inprocess":
            future = self._executor.submit(call_tool_inprocess, name, arguments)
        else:
            future = self._executor.submit(self._call_tool_stdio, name, arguments)
        try:
            return future.result(timeout=self._timeout_sec)
        except FuturesTimeout as e:
            raise McpTimeoutError(
                f"MCP tool {name} timeout after {self._timeout_sec}s"
            ) from e

    def _call_tool_stdio(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        return asyncio.run(self._call_tool_stdio_async(name, arguments))

    async def _call_tool_stdio_async(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        params = StdioServerParameters(
            command=self._server_command,
            args=list(self._server_args),
            cwd=self._server_cwd,
        )
        logger.debug(
            "MCP stdio invoke tool=%s command=%s args=%s cwd=%s",
            name,
            self._server_command,
            self._server_args,
            self._server_cwd,
        )
        async with stdio_client(params) as (read, write):
            async with ClientSession(read, write) as session:
                await session.initialize()
                result = await session.call_tool(name, arguments=arguments)
                return _parse_tool_result(result)

    @staticmethod
    def build_id_hash(user_id: int) -> str:
        return hashlib.sha256(f"user:{user_id}".encode()).hexdigest()

    def query_credit_record(
        self,
        *,
        id_no_hash: str,
        name: str,
        user_id: int,
        query_reason: str = "LOAN_APPROVAL",
    ) -> dict[str, Any]:
        return self.call_tool_sync(
            "query_credit_record",
            {
                "id_no_hash": id_no_hash,
                "name": name,
                "user_id": user_id,
                "query_reason": query_reason,
            },
        )

    def query_court_enforcement_record(
        self,
        *,
        id_no_hash: str,
        name: str,
        user_id: int,
        query_reason: str = "LOAN_APPROVAL",
    ) -> dict[str, Any]:
        return self.call_tool_sync(
            "query_court_enforcement_record",
            {
                "id_no_hash": id_no_hash,
                "name": name,
                "user_id": user_id,
                "query_reason": query_reason,
            },
        )

    def verify_income_stability(
        self,
        *,
        user_id: int,
        monthly_income: float,
        employment_type: str,
        loan_amount: float,
        query_reason: str = "LOAN_APPROVAL",
    ) -> dict[str, Any]:
        return self.call_tool_sync(
            "verify_income_stability",
            {
                "user_id": user_id,
                "monthly_income": monthly_income,
                "employment_type": employment_type,
                "loan_amount": loan_amount,
                "query_reason": query_reason,
            },
        )


def _parse_tool_result(result: Any) -> dict[str, Any]:
    if getattr(result, "isError", False):
        message = _content_to_text(getattr(result, "content", None))
        raise RuntimeError(message or "MCP tool returned error")

    structured = getattr(result, "structuredContent", None)
    if isinstance(structured, dict):
        return structured

    text = _content_to_text(getattr(result, "content", None))
    if not text:
        return {}
    try:
        parsed = json.loads(text)
        if isinstance(parsed, dict):
            return parsed
    except json.JSONDecodeError:
        pass
    return {"raw": text}


def _content_to_text(content: Any) -> str:
    if not content:
        return ""
    parts: list[str] = []
    for block in content:
        text = getattr(block, "text", None)
        if text:
            parts.append(text)
    return "".join(parts)
