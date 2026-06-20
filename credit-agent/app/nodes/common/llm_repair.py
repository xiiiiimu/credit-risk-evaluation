from typing import TYPE_CHECKING, Type, TypeVar

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from pydantic import BaseModel

from app.config import Settings, get_settings
from app.nodes.common.llm_json import PayloadNormalizer, validate_model
from app.nodes.common.chat_llm import invoke_llm
from app.prompts.loader import load_prompt

if TYPE_CHECKING:
    from app.clients.spring_tool_client import SpringToolClient

T = TypeVar("T", bound=BaseModel)


def repair_once(
    llm: ChatOpenAI,
    raw: str,
    model: type[T],
    error: str,
    *,
    tool_client: "SpringToolClient | None" = None,
    trace_id: str | None = None,
    settings: Settings | None = None,
    normalizer: PayloadNormalizer | None = None,
) -> tuple[T | None, str | None]:
    base_prompt = load_prompt(tool_client, "llm_repair", trace_id)
    system = SystemMessage(content=f"{base_prompt} 错误: {error}")
    human = HumanMessage(content=f"原始输出:\n{raw}")
    cfg = settings or get_settings()
    reply = invoke_llm(llm, [system, human], cfg)
    text = reply.content if isinstance(reply.content, str) else str(reply.content)
    return validate_model(text, model, normalizer=normalizer)
