import logging
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from app.clients.spring_tool_client import SpringToolClient

logger = logging.getLogger(__name__)

DEFAULT_PROMPTS: dict[str, str] = {
    "document_review": (
        "你是信贷资料审核助手。仅评估资料完整性，不做审批决定。"
        '输出 JSON：{"docComplete":true|false,"missingDocs":["ID_CARD"],"confidence":0.0-1.0,'
        '"summary":"摘要","ticketTitle":"工单标题","ticketDescription":"描述"}'
    ),
    "credit_assessment": (
        "You are a credit risk assessment assistant. Combine credit bureau and user profile "
        "signals, and do not output APPROVED or REJECTED directly. "
        "creditLevel must be one of LOW, MEDIUM, HIGH. Do not translate enum values. "
        "Never output Chinese enum values such as 良好/中等/高 or 低/中/高风险. "
        'Return JSON: {"creditLevel":"LOW|MEDIUM|HIGH","creditScore":700,"incomeDebtRatio":0.3,'
        '"riskFactors":["factor"],"confidence":0.0-1.0,"summary":"summary"}'
    ),
    "anti_fraud": (
        "你是反欺诈解释助手。根据结构化风险信号生成中文风险摘要，禁止输出 APPROVED/REJECTED。"
        '输出 JSON：{"fraudLevel":"LOW|MEDIUM|HIGH","fraudSignals":["信号"],'
        '"confidence":0.0-1.0,"summary":"摘要"}'
    ),
    "credit_advisory": (
        "你是授信建议助手。仅给出建议额度/利率/期限，禁止输出 APPROVED/REJECTED。"
        '输出 JSON：{"suggestedAmount":50000,"suggestedRate":0.065,"suggestedTerm":12,'
        '"confidence":0.0-1.0,"summary":"摘要"}'
    ),
    "llm_repair": "修复以下 JSON 使其符合 schema，只输出合法 JSON，不要 markdown。",
}


DEFAULT_PROMPT_VERSION = 0


def load_prompt_meta(
    tool_client: "SpringToolClient | None",
    prompt_code: str,
    trace_id: str | None = None,
) -> tuple[str, int]:
    if tool_client is not None:
        try:
            data = tool_client.invoke(
                "get_prompt_config",
                {"promptCode": prompt_code},
                trace_id=trace_id,
                skip_audit=True,
            )
            content = data.get("promptContent") or data.get("prompt_content")
            if content:
                version = data.get("version")
                return str(content), int(version) if version is not None else DEFAULT_PROMPT_VERSION
        except Exception as exc:
            logger.warning("load prompt %s from platform failed: %s", prompt_code, exc)
    return DEFAULT_PROMPTS.get(prompt_code, ""), DEFAULT_PROMPT_VERSION


def load_prompt(
    tool_client: "SpringToolClient | None",
    prompt_code: str,
    trace_id: str | None = None,
) -> str:
    content, _ = load_prompt_meta(tool_client, prompt_code, trace_id)
    return content
