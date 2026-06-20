"""Credit bureau query mock logic shared by the MCP server."""

from __future__ import annotations

import hashlib
from typing import Any


def query_credit_record(
    id_no_hash: str,
    name: str,
    query_reason: str = "LOAN_APPROVAL",
    user_id: int | None = None,
) -> dict[str, Any]:
    seed = int(hashlib.sha256(f"{id_no_hash}:{name}".encode()).hexdigest()[:8], 16)
    credit_score = 580 + (seed % 220)
    overdue = 0 if credit_score >= 650 else (1 if credit_score >= 600 else 3)
    query_count = seed % 5
    blacklist_hit = credit_score < 550
    total_debt = 50000 + (seed % 200000)
    evidence = [
        {
            "evidenceId": "mcp_credit_score",
            "sourceTool": "query_credit_record",
            "label": "credit score",
            "value": credit_score,
        },
        {
            "evidenceId": "mcp_credit_overdue_count_24m",
            "sourceTool": "query_credit_record",
            "label": "overdue count in 24 months",
            "value": overdue,
        },
        {
            "evidenceId": "mcp_credit_blacklist_hit",
            "sourceTool": "query_credit_record",
            "label": "credit blacklist hit",
            "value": blacklist_hit,
        },
    ]
    return {
        "creditScore": credit_score,
        "overdueCount24m": overdue,
        "totalDebt": total_debt,
        "queryCount3m": query_count,
        "blacklistHit": blacklist_hit,
        "queryReason": query_reason,
        "userId": user_id,
        "mock": True,
        "evidence": evidence,
    }
