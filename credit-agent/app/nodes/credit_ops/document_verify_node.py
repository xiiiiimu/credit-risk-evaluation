from typing import Any

from app.clients.spring_tool_client import SpringToolClient
from app.nodes.common.workflow_trace import trace_node
from app.nodes.credit_ops.credit_workflow_trace import NodeTimer, trace_credit_node
from app.state.credit_ops_state import CreditOpsState


def document_verify_tool_node(state: CreditOpsState, tool_client: SpringToolClient) -> dict[str, Any]:
    wf = state.get("workflow_id")
    tid = state.get("trace_id")
    timer = NodeTimer()
    trace_node(tool_client, wf, tid, "DocumentVerifyTool", "start")

    resp = tool_client.invoke(
        "verify_application_documents",
        {
            "userId": state["user_id"],
            "productId": state["product_id"],
            "content": state.get("content"),
        },
        trace_id=tid,
    )
    data = resp or {}
    verified = bool(data.get("verifiedDocuments")) and bool(state.get("verified_documents"))

    trace_node(tool_client, wf, tid, "DocumentVerifyTool", "end", decision=str(verified))
    trace_credit_node(
        tool_client, state, "document_verify", timer.elapsed_ms(),
        tool_calls=["verify_application_documents"],
    )
    doc_score = float(data.get("documentScore") or (0.9 if verified else 0.4))
    return {"verify_result": data, "verified_documents": verified, "document_score": doc_score}
