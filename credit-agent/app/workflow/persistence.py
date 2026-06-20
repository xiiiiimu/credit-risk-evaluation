from __future__ import annotations

import json
import logging
from typing import Any

from app.clients.spring_tool_client import SpringToolClient

logger = logging.getLogger("credit.workflow.trace")


class WorkflowPersistenceClient:
  def __init__(self, tool_client: SpringToolClient) -> None:
    self._tool = tool_client

  def resolve_idempotent(self, workflow_id: str, trace_id: str | None) -> dict[str, Any]:
    return self._tool.invoke(
      "resolve_workflow_idempotent",
      {"workflowId": workflow_id},
      trace_id=trace_id,
    )

  def acquire_execution(
    self,
    workflow_id: str,
    trace_id: str | None,
    task_id: int | None,
    application_id: int | None,
    lock_owner: str | None,
  ) -> dict[str, Any]:
    return self._tool.invoke(
      "acquire_workflow_execution",
      {
        "workflowId": workflow_id,
        "traceId": trace_id,
        "taskId": task_id,
        "applicationId": application_id,
        "lockOwner": lock_owner,
      },
      trace_id=trace_id,
    )

  def release_lock(self, workflow_id: str, trace_id: str | None) -> None:
    self._tool.invoke(
      "release_workflow_lock",
      {"workflowId": workflow_id},
      trace_id=trace_id,
    )

  def start_workflow(
    self,
    workflow_id: str,
    trace_id: str | None,
    task_id: int | None,
    application_id: int | None,
  ) -> None:
    self._tool.invoke(
      "start_workflow",
      {
        "workflowId": workflow_id,
        "traceId": trace_id,
        "taskId": task_id,
        "applicationId": application_id,
      },
      trace_id=trace_id,
    )

  def begin_node(
    self,
    workflow_id: str,
    node_name: str,
    agent_name: str | None,
    trace_id: str | None,
    state: dict[str, Any],
  ) -> None:
    self._tool.invoke(
      "begin_workflow_node",
      {
        "workflowId": workflow_id,
        "nodeName": node_name,
        "agentName": agent_name,
        "traceId": trace_id,
        "input": _json_safe(state),
      },
      trace_id=trace_id,
    )

  def complete_node(
    self,
    workflow_id: str,
    node_name: str,
    agent_name: str | None,
    trace_id: str | None,
    *,
    success: bool,
    output: dict[str, Any] | None,
    error_code: str | None,
    error_msg: str | None,
    retry_count: int,
    cost_time_ms: int,
  ) -> None:
    self._tool.invoke(
      "complete_workflow_node",
      {
        "workflowId": workflow_id,
        "nodeName": node_name,
        "agentName": agent_name,
        "traceId": trace_id,
        "success": success,
        "output": _json_safe(output or {}),
        "errorCode": error_code,
        "errorMsg": error_msg,
        "retryCount": retry_count,
        "costTimeMs": cost_time_ms,
      },
      trace_id=trace_id,
    )

  def save_checkpoint(
    self,
    workflow_id: str,
    current_node: str,
    state: dict[str, Any],
    history: list[dict[str, Any]],
    retry_count: int,
    trace_id: str | None,
  ) -> None:
    self._tool.invoke(
      "save_workflow_checkpoint",
      {
        "workflowId": workflow_id,
        "currentNode": current_node,
        "state": _json_safe(state),
        "history": history,
        "retryCount": retry_count,
      },
      trace_id=trace_id,
    )

  def load_checkpoint(self, workflow_id: str, trace_id: str | None) -> dict[str, Any] | None:
    data = self._tool.invoke(
      "load_workflow_checkpoint",
      {"workflowId": workflow_id},
      trace_id=trace_id,
    )
    return data or None

  def finish_workflow(
    self,
    workflow_id: str,
    status: str,
    trace_id: str | None,
    result: dict[str, Any] | None,
    retry_count: int = 0,
  ) -> None:
    self._tool.invoke(
      "finish_workflow",
      {
        "workflowId": workflow_id,
        "status": status,
        "traceId": trace_id,
        "result": _json_safe(result or {}),
        "retryCount": retry_count,
      },
      trace_id=trace_id,
    )


def _json_safe(value: Any) -> Any:
  try:
    return json.loads(json.dumps(value, default=str))
  except (TypeError, ValueError):
    return str(value)
