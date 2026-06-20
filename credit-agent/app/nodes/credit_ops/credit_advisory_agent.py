"""@deprecated 主流程已移除 credit_advisory，额度/利率/期限由 Java Rule Engine 决定。"""

from langchain_core.messages import HumanMessage, SystemMessage

from app.audit.recorder import AuditContext
from app.clients.spring_tool_client import SpringToolClient
from app.config import Settings
from app.nodes.common.chat_llm import chat_llm, invoke_llm
from app.nodes.common.schema_validator import validate_or_repair_strict
from app.nodes.common.workflow_trace import trace_node
from app.nodes.credit_ops.credit_workflow_trace import NodeTimer, trace_credit_node
from app.prompts.loader import load_prompt_meta
from app.schemas.credit_advisory import CreditAdvisorySchema
from app.state.credit_ops_state import CreditOpsState


def credit_advisory_agent(
    state: CreditOpsState, settings: Settings, tool_client: SpringToolClient
) -> dict[str, Any]:
    wf = state.get("workflow_id")
    tid = state.get("trace_id")
    timer = NodeTimer()
    trace_node(tool_client, wf, tid, "CreditAdvisoryAgent", "start")

    product = state.get("product_info") or {}
    max_amount = float(product.get("maxAmount") or product.get("max_amount") or 200000)
    apply_amount = float(state.get("apply_amount") or 50000)
    base_rate = float(product.get("interestRate") or product.get("interest_rate") or 0.065)
    default_term = int(state.get("apply_term") or 12)

    llm = chat_llm(settings)
    prompt, prompt_version = load_prompt_meta(tool_client, "credit_advisory", tid)
    audit = AuditContext(
        workflow_id=wf,
        trace_id=tid,
        node_name="credit_advisory",
        prompt_code="credit_advisory",
        prompt_version=prompt_version,
    )
    system = SystemMessage(content=prompt)
    human = HumanMessage(
        content=json.dumps(
            {
                "assessment": state.get("credit_assessment_json"),
                "antiFraud": state.get("anti_fraud_json"),
                "applyAmount": apply_amount,
                "maxAmount": max_amount,
            },
            ensure_ascii=False,
        )
    )
    raw = invoke_llm(llm, [system, human], settings, tool_client=tool_client, audit=audit).content
    raw = raw if isinstance(raw, str) else str(raw)

    outcome = validate_or_repair_strict(
        raw, CreditAdvisorySchema, llm, tool_client=tool_client, trace_id=tid
    )
    validated = outcome.model
    assert validated is not None
    advisory_json = validated.to_legacy_json()
    advisory_result = validated.model_dump(by_alias=True)
    suggest_amount = validated.suggested_amount
    suggest_rate = validated.suggested_rate
    suggest_term = validated.suggested_term

    trace_credit_node(
        tool_client,
        state,
        "credit_advisory",
        timer.elapsed_ms(),
        tool_calls=["LLM"],
        validation_outcome=outcome,
    )
    return {
        "credit_advisory_json": advisory_json,
        "credit_advisory_result": advisory_result,
        "suggest_amount": suggest_amount,
        "suggest_rate": suggest_rate,
        "suggest_term": suggest_term,
        "reason": advisory_json.get("rationale") or advisory_json.get("summary") or "",
        "degraded": outcome.degraded,
        "agent_vote_credit_advisory": "SUGGEST_MANUAL",
    }
