from typing import Any

from app.clients.spring_tool_client import SpringToolClient
from app.nodes.common.workflow_trace import trace_node
from app.nodes.credit_ops.credit_workflow_trace import NodeTimer, trace_credit_node
from app.state.credit_ops_state import CreditOpsState


def final_response_node(state: CreditOpsState, tool_client: SpringToolClient) -> dict[str, Any]:
    wf = state.get("workflow_id")
    tid = state.get("trace_id")
    timer = NodeTimer()
    trace_node(tool_client, wf, tid, "FinalResponseNode", "start")

    suggestion = state.get("agent_suggestion") or "SUGGEST_MANUAL"
    summary = state.get("ai_summary") or "信贷风控分析完成"
    amount = state.get("suggest_amount")
    rate = state.get("suggest_rate")

    answer = (
        f"{summary}\n"
        f"Agent建议：{suggestion}。"
        f"建议授信额度：{amount}，利率：{rate}。"
        f"最终审批由平台规则引擎裁定。"
    )

    trace_node(tool_client, wf, tid, "FinalResponseNode", "end")
    trace_credit_node(tool_client, state, "final", timer.elapsed_ms())
    return {"answer": answer}
