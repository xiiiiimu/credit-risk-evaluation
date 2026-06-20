from __future__ import annotations

from functools import partial
from typing import Any, Callable

from app.clients.mcp_credit_client import McpCreditClient
from app.clients.spring_tool_client import SpringToolClient
from app.config import Settings
from app.nodes.credit_ops.anti_fraud_agent import anti_fraud_agent
from app.nodes.credit_ops.consensus_node import consensus_node
from app.nodes.credit_ops.credit_assessment_agent import credit_assessment_agent
from app.nodes.credit_ops.document_review_agent import document_review_agent
from app.nodes.credit_ops.document_verify_node import document_verify_tool_node
from app.nodes.credit_ops.ocr_preprocess_node import input_fusion_node, ocr_preprocess_node
from app.nodes.credit_ops.final_response_node import final_response_node
from app.nodes.credit_ops.memory_load_node import load_memory
from app.nodes.credit_ops.suggestion_routing_node import suggestion_routing_node
from app.workflow.constants import AGENT_NODES, NODE_ORDER
from app.workflow.response_mapper import response_dict_to_state, state_to_response_dict
from app.workflow.persistence import WorkflowPersistenceClient
from app.workflow.retry import WorkflowManualReviewRequired, execute_with_retry
from app.resilience.agent_runtime import AgentRuntimeGuard


def build_node_callables(settings: Settings, tool_client: SpringToolClient) -> dict[str, Callable[[dict[str, Any]], dict[str, Any]]]:
  mcp_client = McpCreditClient(settings)
  return {
    "load_memory": partial(load_memory, tool_client=tool_client),
    "ocr_preprocess": partial(ocr_preprocess_node, tool_client=tool_client),
    "input_fusion": partial(input_fusion_node, tool_client=tool_client),
    "document_review": partial(document_review_agent, settings=settings, tool_client=tool_client),
    "document_verify": partial(document_verify_tool_node, tool_client=tool_client),
    "credit_assessment": partial(
      credit_assessment_agent,
      settings=settings,
      tool_client=tool_client,
      mcp_client=mcp_client,
    ),
    "anti_fraud": partial(anti_fraud_agent, settings=settings, tool_client=tool_client),
    "consensus": partial(consensus_node, tool_client=tool_client),
    "suggestion_routing": partial(suggestion_routing_node, tool_client=tool_client),
    "final": partial(final_response_node, tool_client=tool_client),
  }


def _merge_state(state: dict[str, Any], delta: dict[str, Any]) -> dict[str, Any]:
  merged = dict(state)
  merged.update(delta)
  return merged


def _resume_start_index(checkpoint: dict[str, Any] | None) -> int:
  if not checkpoint or not checkpoint.get("currentNode"):
    return 0
  current = checkpoint["currentNode"]
  if current not in NODE_ORDER:
    return 0
  return NODE_ORDER.index(current) + 1


def run_credit_workflow(settings: Settings, initial: dict[str, Any]) -> dict[str, Any]:
  workflow_id = initial.get("workflow_id")
  if not workflow_id:
    raise ValueError("workflow_id is required")

  trace_id = initial.get("trace_id")
  tool_client = SpringToolClient(settings)
  persistence = WorkflowPersistenceClient(tool_client)

  idem = persistence.resolve_idempotent(workflow_id, trace_id)
  action = idem.get("action")
  if action == "RETURN_RESULT":
    cached = idem.get("result") or {}
    if isinstance(cached, dict):
      return response_dict_to_state(cached) if cached.get("workflowId") else cached
  if action == "WAIT":
    raise RuntimeError(f"workflow {workflow_id} is still running at node {idem.get('currentNode')}")

  status = idem.get("status")
  lock_held = False
  if status != "RUNNING":
    acquire = persistence.acquire_execution(
      workflow_id,
      trace_id,
      initial.get("task_id"),
      initial.get("application_id"),
      lock_owner=f"agent-{workflow_id}",
    )
    if acquire.get("action") == "RETURN_RESULT":
      cached = acquire.get("result") or {}
      if isinstance(cached, dict):
        return response_dict_to_state(cached) if cached.get("workflowId") else cached
    if not acquire.get("acquired"):
      raise RuntimeError(f"workflow {workflow_id} is still running at node {acquire.get('currentNode')}")
    lock_held = True
  else:
    persistence.start_workflow(
      workflow_id,
      trace_id,
      initial.get("task_id"),
      initial.get("application_id"),
    )

  try:
    return _run_workflow_body(
      settings, initial, workflow_id, trace_id, tool_client, persistence, None
    )
  finally:
    if lock_held:
      persistence.release_lock(workflow_id, trace_id)


def _run_workflow_body(
  settings: Settings,
  initial: dict[str, Any],
  workflow_id: str,
  trace_id: str | None,
  tool_client: SpringToolClient,
  persistence: WorkflowPersistenceClient,
  runtime_guard: AgentRuntimeGuard | None,
) -> dict[str, Any]:
  checkpoint_raw = persistence.load_checkpoint(workflow_id, trace_id)
  checkpoint = checkpoint_raw if checkpoint_raw and checkpoint_raw.get("currentNode") else None
  state = dict(initial)
  history: list[dict[str, Any]] = []
  if checkpoint:
    saved_state = checkpoint.get("state") or {}
    if isinstance(saved_state, dict):
      state.update(saved_state)
    saved_history = checkpoint.get("history") or []
    if isinstance(saved_history, list):
      history = list(saved_history)

  start_index = _resume_start_index(checkpoint)
  nodes = build_node_callables(settings, tool_client)
  workflow_retry_count = int((checkpoint or {}).get("retryCount") or 0)
  runtime_guard = AgentRuntimeGuard(settings)

  try:
    for node_name in NODE_ORDER[start_index:]:
      agent_name = AGENT_NODES.get(node_name)
      persistence.begin_node(workflow_id, node_name, agent_name, trace_id, state)
      node_fn = nodes[node_name]

      def _run_node(fn: Callable[[dict[str, Any]], dict[str, Any]] = node_fn) -> dict[str, Any]:
        def _execute() -> dict[str, Any]:
          delta = fn(state)
          if not isinstance(delta, dict):
            return {}
          return delta

        return runtime_guard.run_node(
          _execute,
          agent_name=agent_name,
          node_name=node_name,
        )

      try:
        delta, retry_count, cost_ms = execute_with_retry(
          _run_node,
          workflow_id=workflow_id,
          trace_id=trace_id,
          node_name=node_name,
          agent_name=agent_name,
        )
        workflow_retry_count = max(workflow_retry_count, retry_count)
        state = _merge_state(state, delta)
        history.append({"node": node_name, "status": "SUCCESS", "retryCount": retry_count})
        persistence.complete_node(
          workflow_id,
          node_name,
          agent_name,
          trace_id,
          success=True,
          output=delta,
          error_code=None,
          error_msg=None,
          retry_count=retry_count,
          cost_time_ms=cost_ms,
        )
        persistence.save_checkpoint(workflow_id, node_name, state, history, workflow_retry_count, trace_id)
      except WorkflowManualReviewRequired as exc:
        workflow_retry_count = max(workflow_retry_count, exc.retry_count)
        persistence.complete_node(
          workflow_id,
          node_name,
          agent_name,
          trace_id,
          success=False,
          output=state,
          error_code=exc.error_code,
          error_msg=exc.error_msg,
          retry_count=exc.retry_count,
          cost_time_ms=0,
        )
        state = _merge_state(state, {"need_manual_review": True, "reason": exc.error_msg})
        persistence.save_checkpoint(workflow_id, node_name, state, history, workflow_retry_count, trace_id)
        persistence.finish_workflow(
          workflow_id,
          "MANUAL_REVIEW",
          trace_id,
          state_to_response_dict(state, initial),
          workflow_retry_count,
        )
        return state

    persistence.finish_workflow(
      workflow_id,
      "SUCCESS",
      trace_id,
      state_to_response_dict(state, initial),
      workflow_retry_count,
    )
    return state
  except Exception as exc:
    persistence.finish_workflow(workflow_id, "FAILED", trace_id, state, workflow_retry_count)
    raise RuntimeError(str(exc)) from exc
