import json
from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage

from app.audit.recorder import AuditContext
from app.clients.spring_tool_client import SpringToolClient
from app.config import Settings
from app.nodes.common.chat_llm import chat_llm, invoke_llm
from app.nodes.common.schema_validator import validate_or_repair_strict
from app.nodes.common.workflow_trace import trace_node
from app.nodes.credit_ops.credit_workflow_trace import NodeTimer, trace_credit_node
from app.prompts.loader import load_prompt_meta
from app.schemas.anti_fraud import AntiFraudSchema
from app.state.credit_ops_state import CreditOpsState


def anti_fraud_agent(state: CreditOpsState, settings: Settings, tool_client: SpringToolClient) -> dict[str, Any]:
    wf = state.get("workflow_id")
    tid = state.get("trace_id")
    timer = NodeTimer()
    trace_node(tool_client, wf, tid, "AntiFraudAgent", "start")

    payload = tool_client.invoke(
        "evaluate_fraud_signals",
        {
            "userId": state["user_id"],
            "applicationId": None,
            "contentHint": state.get("content"),
        },
        trace_id=tid,
    ) or {}

    fraud_score = int(payload.get("fraudScore") or 0)
    hit_rules = list(payload.get("hitRules") or [])
    signals = payload.get("signals") or {}

    llm = chat_llm(settings)
    prompt, prompt_version = load_prompt_meta(tool_client, "anti_fraud", tid)
    audit = AuditContext(
        workflow_id=wf,
        trace_id=tid,
        node_name="anti_fraud",
        prompt_code="anti_fraud",
        prompt_version=prompt_version,
    )
    system = SystemMessage(content=prompt)
    human = HumanMessage(
        content=json.dumps(
            {
                "signals": signals,
                "hitRules": hit_rules,
                "fraudScore": fraud_score,
                "productContext": (state.get("unified_risk_context") or {}).get("productContext")
                    or state.get("product_context"),
            },
            ensure_ascii=False,
        )
    )
    raw = invoke_llm(llm, [system, human], settings, tool_client=tool_client, audit=audit).content
    raw = raw if isinstance(raw, str) else str(raw)

    outcome = validate_or_repair_strict(
        raw, AntiFraudSchema, llm, tool_client=tool_client, trace_id=tid
    )
    validated = outcome.model
    assert validated is not None
    summary = validated.summary[:500]
    llm_confidence = validated.confidence
    fraud_result = validated.model_dump(by_alias=True)

    vote = "SUGGEST_REJECT" if fraud_score >= 70 else (
        "SUGGEST_MANUAL" if fraud_score >= 40 else "SUGGEST_APPROVE"
    )

    trace_credit_node(
        tool_client,
        state,
        "anti_fraud",
        timer.elapsed_ms(),
        tool_calls=["evaluate_fraud_signals", "LLM"],
        validation_outcome=outcome,
    )
    return {
        "anti_fraud_json": {
            "fraudScore": fraud_score,
            "hitRules": hit_rules,
            "signals": signals,
            "summary": summary,
            **(fraud_result or {}),
        },
        "anti_fraud_result": fraud_result,
        "fraud_score": fraud_score,
        "fraud_hit_rules": hit_rules,
        "anti_fraud_confidence": llm_confidence,
        "degraded": outcome.degraded,
        "agent_vote_anti_fraud": vote,
    }
