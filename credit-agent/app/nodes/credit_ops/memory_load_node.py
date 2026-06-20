from typing import Any

from app.clients.spring_tool_client import SpringToolClient
from app.nodes.common.workflow_trace import trace_node
from app.nodes.credit_ops.credit_workflow_trace import NodeTimer, trace_credit_node
from app.state.credit_ops_state import CreditOpsState


def load_memory(state: CreditOpsState, tool_client: SpringToolClient) -> dict[str, Any]:
    wf = state.get("workflow_id")
    tid = state.get("trace_id")
    timer = NodeTimer()
    trace_node(tool_client, wf, tid, "LoadMemoryNode", "start")

    user_mem = tool_client.invoke("get_user_memory", {"userId": state["user_id"]}, trace_id=tid)
    product = tool_client.invoke("get_credit_product", {"productId": state["product_id"]}, trace_id=tid)
    history = tool_client.invoke("get_user_credit_history", {"userId": state["user_id"]}, trace_id=tid)

    merged_memory = dict(user_mem or {})
    if history:
        merged_memory.update(history)

    trace_node(tool_client, wf, tid, "LoadMemoryNode", "end")
    trace_credit_node(
        tool_client, state, "load_memory", timer.elapsed_ms(),
        tool_calls=["get_user_memory", "get_user_credit_history", "get_credit_product"],
    )
    return {
        "user_memory": merged_memory,
        "product_info": product or {},
        "credit_history": history or {},
        "recent_apply_count_7d": int((history or {}).get("recentApplyCount7d") or 0),
    }
