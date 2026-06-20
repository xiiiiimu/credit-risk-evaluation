"""标准 MCP Server（stdio）：暴露征信与外部风控查询工具。"""

from __future__ import annotations

from mcp.server.fastmcp import FastMCP

from app.mcp_server.bureau_logic import query_credit_record
from app.mcp_server.external_risk_logic import (
    query_court_enforcement_record,
    verify_income_stability,
)

mcp = FastMCP("credit-bureau")


@mcp.tool(name="query_credit_record")
def query_credit_record_tool(
    id_no_hash: str,
    name: str,
    query_reason: str = "LOAN_APPROVAL",
    user_id: int | None = None,
) -> dict:
    """查询借款人征信摘要（脱敏 id_no_hash + 姓名）。"""
    return query_credit_record(
        id_no_hash=id_no_hash,
        name=name,
        query_reason=query_reason,
        user_id=user_id,
    )


@mcp.tool(name="query_court_enforcement_record")
def query_court_enforcement_record_tool(
    id_no_hash: str,
    name: str,
    user_id: int,
    query_reason: str = "LOAN_APPROVAL",
) -> dict:
    """查询法院执行与失信记录。"""
    return query_court_enforcement_record(
        id_no_hash=id_no_hash,
        name=name,
        query_reason=query_reason,
        user_id=user_id,
    )


@mcp.tool(name="verify_income_stability")
def verify_income_stability_tool(
    user_id: int,
    monthly_income: float,
    employment_type: str,
    loan_amount: float,
    query_reason: str = "LOAN_APPROVAL",
) -> dict:
    """验证第三方收入稳定性信号。"""
    return verify_income_stability(
        user_id=user_id,
        monthly_income=monthly_income,
        employment_type=employment_type,
        loan_amount=loan_amount,
        query_reason=query_reason,
    )
