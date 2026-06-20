import time
from typing import Any

from app.clients.spring_tool_client import SpringToolClient
from app.nodes.common.schema_validator import ValidationOutcome


def build_tool_calls_with_validation(
    tool_calls: list[Any] | None,
    outcome: ValidationOutcome | None,
    node_name: str,
) -> list[Any]:
    merged: list[Any] = list(tool_calls or [])
    if outcome is None:
        return merged
    if outcome.validation_error or outcome.repair_triggered:
        merged.append(
            {
                "schemaValidation": {
                    "nodeName": node_name,
                    "validationError": outcome.validation_error,
                    "repairTriggered": outcome.repair_triggered,
                    "repairSuccess": outcome.repair_success,
                    "degraded": outcome.degraded,
                }
            }
        )
    return merged


def trace_credit_node(
    tool_client: SpringToolClient,
    state: dict[str, Any],
    node_name: str,
    latency_ms: int,
    status: str = "SUCCESS",
    error_message: str | None = None,
    tool_calls: Any = None,
    mcp_latency_ms: int | None = None,
    validation_outcome: ValidationOutcome | None = None,
) -> None:
    """写入 tb_credit_workflow_trace，必须携带 application_id / task_id / workflow_id / trace_id。"""
    workflow_id = state.get("workflow_id")
    if not workflow_id:
        return

    if validation_outcome and validation_outcome.degraded and status == "SUCCESS":
        status = "DEGRADED"

    calls = tool_calls
    if isinstance(tool_calls, list):
        calls = build_tool_calls_with_validation(tool_calls, validation_outcome, node_name)
    elif validation_outcome and (validation_outcome.validation_error or validation_outcome.repair_triggered):
        calls = build_tool_calls_with_validation([], validation_outcome, node_name)

    try:
        tool_client.invoke(
            "save_credit_workflow_trace",
            {
                "applicationId": state.get("application_id"),
                "taskId": state.get("task_id"),
                "workflowId": workflow_id,
                "traceId": state.get("trace_id"),
                "nodeName": node_name,
                "latencyMs": latency_ms,
                "status": status,
                "errorMessage": error_message,
                "toolCalls": calls,
                "mcpLatencyMs": mcp_latency_ms,
            },
            trace_id=state.get("trace_id"),
        )
    except Exception:
        pass


class NodeTimer:
    def __init__(self) -> None:
        self._start = time.time()

    def elapsed_ms(self) -> int:
        return int((time.time() - self._start) * 1000)
