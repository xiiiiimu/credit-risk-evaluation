"""Compatibility layer for MCP tool exposure."""

from __future__ import annotations

from typing import Any

from app.mcp_server.bureau_logic import query_credit_record
from app.mcp_server.external_risk_logic import (
    query_court_enforcement_record,
    verify_income_stability,
)


def list_tools() -> list[dict[str, Any]]:
    return [
        {
            "name": "query_credit_record",
            "description": "Query borrower credit bureau summary.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "id_no_hash": {"type": "string"},
                    "name": {"type": "string"},
                    "query_reason": {"type": "string", "default": "LOAN_APPROVAL"},
                    "user_id": {"type": "integer"},
                },
                "required": ["id_no_hash", "name"],
            },
        },
        {
            "name": "query_court_enforcement_record",
            "description": "Query court enforcement and dishonesty records.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "id_no_hash": {"type": "string"},
                    "name": {"type": "string"},
                    "user_id": {"type": "integer"},
                    "query_reason": {"type": "string", "default": "LOAN_APPROVAL"},
                },
                "required": ["id_no_hash", "name", "user_id"],
            },
        },
        {
            "name": "verify_income_stability",
            "description": "Verify third-party income stability signal.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "user_id": {"type": "integer"},
                    "monthly_income": {"type": "number"},
                    "employment_type": {"type": "string"},
                    "loan_amount": {"type": "number"},
                    "query_reason": {"type": "string", "default": "LOAN_APPROVAL"},
                },
                "required": ["user_id", "monthly_income", "employment_type", "loan_amount"],
            },
        },
    ]


def call_tool(name: str, arguments: dict[str, Any]) -> dict[str, Any]:
    if name == "query_credit_record":
        return query_credit_record(
            id_no_hash=str(arguments.get("id_no_hash", "")),
            name=str(arguments.get("name", "")),
            query_reason=str(arguments.get("query_reason", "LOAN_APPROVAL")),
            user_id=arguments.get("user_id"),
        )
    if name == "query_court_enforcement_record":
        return query_court_enforcement_record(
            id_no_hash=str(arguments.get("id_no_hash", "")),
            name=str(arguments.get("name", "")),
            query_reason=str(arguments.get("query_reason", "LOAN_APPROVAL")),
            user_id=int(arguments.get("user_id") or 0),
        )
    if name == "verify_income_stability":
        return verify_income_stability(
            user_id=int(arguments.get("user_id") or 0),
            monthly_income=float(arguments.get("monthly_income") or 0),
            employment_type=str(arguments.get("employment_type") or ""),
            loan_amount=float(arguments.get("loan_amount") or 0),
            query_reason=str(arguments.get("query_reason", "LOAN_APPROVAL")),
        )
    raise ValueError(f"Unknown tool: {name}")
