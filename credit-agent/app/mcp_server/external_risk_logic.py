from __future__ import annotations

import hashlib
from decimal import Decimal, ROUND_HALF_UP
from typing import Any


def _seed_int(*parts: Any) -> int:
    raw = "|".join("" if part is None else str(part) for part in parts)
    return int(hashlib.sha256(raw.encode()).hexdigest()[:12], 16)


def _to_decimal(value: float | int, digits: str = "0.01") -> float:
    return float(Decimal(str(value)).quantize(Decimal(digits), rounding=ROUND_HALF_UP))


def query_court_enforcement_record(
    id_no_hash: str,
    name: str,
    user_id: int | None = None,
    query_reason: str = "LOAN_APPROVAL",
) -> dict[str, Any]:
    seed = _seed_int("court", id_no_hash, name, user_id, query_reason)
    numeric_user_id = int(user_id or 0)

    if numeric_user_id and numeric_user_id % 11 == 0:
        enforcement_count = 4
        dishonesty_hit = True
    elif numeric_user_id and numeric_user_id % 5 == 0:
        enforcement_count = 2
        dishonesty_hit = False
    else:
        enforcement_count = seed % 2
        dishonesty_hit = False

    if enforcement_count == 0:
        total_amount = 0.0
    else:
        total_amount = _to_decimal(enforcement_count * (20000 + (seed % 9) * 8500))

    evidence = [
        {
            "evidenceId": "mcp_court_enforcement_count",
            "sourceTool": "query_court_enforcement_record",
            "label": "court enforcement count",
            "value": enforcement_count,
        },
        {
            "evidenceId": "mcp_court_enforcement_amount",
            "sourceTool": "query_court_enforcement_record",
            "label": "court enforcement total amount",
            "value": total_amount,
        },
        {
            "evidenceId": "mcp_court_dishonesty_hit",
            "sourceTool": "query_court_enforcement_record",
            "label": "dishonesty enforcement hit",
            "value": dishonesty_hit,
        },
    ]
    return {
        "userId": user_id,
        "hasEnforcementRecord": enforcement_count > 0,
        "enforcementCount": enforcement_count,
        "totalEnforcementAmount": total_amount,
        "dishonestyHit": dishonesty_hit,
        "queryReason": query_reason,
        "evidence": evidence,
    }


def verify_income_stability(
    user_id: int,
    monthly_income: float,
    employment_type: str,
    loan_amount: float,
    query_reason: str = "LOAN_APPROVAL",
) -> dict[str, Any]:
    normalized_type = (employment_type or "UNKNOWN").strip().upper()
    seed = _seed_int("income", user_id, monthly_income, normalized_type, loan_amount, query_reason)

    base_income = 6500 + (user_id % 6) * 1800 + (seed % 1200)
    employment_bonus = {
        "SALARIED": 2500,
        "PUBLIC_SERVANT": 3200,
        "SELF_EMPLOYED": 1400,
        "CONTRACTOR": 800,
        "FREELANCER": 300,
    }.get(normalized_type, 1000)
    estimated_income = _to_decimal(base_income + employment_bonus)
    debt_to_income_ratio = _to_decimal(float(loan_amount or 0) / max(estimated_income * 12, 1.0), "0.0001")

    declared_income = float(monthly_income or 0)
    income_verified = declared_income >= estimated_income * 0.65 and debt_to_income_ratio < 1.15

    if normalized_type in {"PUBLIC_SERVANT", "SALARIED"} and income_verified and debt_to_income_ratio < 0.45:
        stability_level = "HIGH"
    elif income_verified and debt_to_income_ratio < 0.75:
        stability_level = "MEDIUM"
    else:
        stability_level = "LOW"

    evidence = [
        {
            "evidenceId": "mcp_income_verified",
            "sourceTool": "verify_income_stability",
            "label": "income verified",
            "value": income_verified,
        },
        {
            "evidenceId": "mcp_income_stability_level",
            "sourceTool": "verify_income_stability",
            "label": "income stability level",
            "value": stability_level,
        },
        {
            "evidenceId": "mcp_estimated_monthly_income",
            "sourceTool": "verify_income_stability",
            "label": "estimated monthly income",
            "value": estimated_income,
        },
        {
            "evidenceId": "mcp_debt_to_income_ratio",
            "sourceTool": "verify_income_stability",
            "label": "debt to income ratio",
            "value": debt_to_income_ratio,
        },
    ]
    return {
        "userId": user_id,
        "incomeVerified": income_verified,
        "incomeStabilityLevel": stability_level,
        "estimatedMonthlyIncome": estimated_income,
        "debtToIncomeRatio": debt_to_income_ratio,
        "queryReason": query_reason,
        "evidence": evidence,
    }
