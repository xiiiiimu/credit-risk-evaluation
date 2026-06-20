from typing import Any

from app.clients.spring_tool_client import SpringToolClient
from app.nodes.common.workflow_trace import trace_node
from app.nodes.credit_ops.credit_workflow_trace import NodeTimer, trace_credit_node
from app.state.credit_ops_state import CreditOpsState


def suggestion_routing_node(state: CreditOpsState, tool_client: SpringToolClient) -> dict[str, Any]:
    wf = state.get("workflow_id")
    tid = state.get("trace_id")
    timer = NodeTimer()
    trace_node(tool_client, wf, tid, "SuggestionRoutingNode", "start")

    suggestion = state.get("consensus_suggestion") or state.get("agent_suggestion") or "SUGGEST_MANUAL"

    trace_node(tool_client, wf, tid, "SuggestionRoutingNode", "end", decision=suggestion)
    trace_credit_node(tool_client, state, "suggestion_routing", timer.elapsed_ms())
    return {"agent_suggestion": suggestion}
