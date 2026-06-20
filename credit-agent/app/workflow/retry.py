from __future__ import annotations

import logging
import time
from collections.abc import Callable
from typing import Any, TypeVar

from app.nodes.common.schema_validator import SchemaValidationError
from app.workflow.constants import MAX_NODE_RETRIES, RETRY_BACKOFF_SEC

logger = logging.getLogger("credit.workflow.trace")

T = TypeVar("T")


class WorkflowManualReviewRequired(Exception):
  def __init__(self, node_name: str, error_code: str, error_msg: str, retry_count: int) -> None:
    super().__init__(error_msg)
    self.node_name = node_name
    self.error_code = error_code
    self.error_msg = error_msg
    self.retry_count = retry_count


def execute_with_retry(
  fn: Callable[[], T],
  *,
  workflow_id: str,
  trace_id: str | None,
  node_name: str,
  agent_name: str | None,
) -> tuple[T, int, int]:
  last_error: Exception | None = None
  for attempt in range(MAX_NODE_RETRIES + 1):
    retry_count = attempt
    start = time.time()
    try:
      result = fn()
      cost_ms = int((time.time() - start) * 1000)
      _log_success(workflow_id, trace_id, node_name, agent_name, retry_count, cost_ms)
      return result, retry_count, cost_ms
    except WorkflowManualReviewRequired:
      raise
    except SchemaValidationError as exc:
      raise WorkflowManualReviewRequired(
        node_name,
        "SCHEMA_VALIDATION_FAILED",
        exc.outcome.validation_error or str(exc),
        retry_count,
      ) from exc
    except Exception as exc:
      last_error = exc
      cost_ms = int((time.time() - start) * 1000)
      _log_failure(
        workflow_id,
        trace_id,
        node_name,
        agent_name,
        retry_count,
        cost_ms,
        "NODE_EXEC_ERROR",
        str(exc),
      )
      if attempt < MAX_NODE_RETRIES:
        backoff = RETRY_BACKOFF_SEC[attempt]
        logger.warning(
          "workflow retry workflow_id=%s node_name=%s agent_name=%s retry_count=%s backoff=%ss",
          workflow_id,
          node_name,
          agent_name,
          retry_count + 1,
          backoff,
        )
        time.sleep(backoff)
        continue
      raise WorkflowManualReviewRequired(
        node_name,
        "NODE_MAX_RETRY",
        str(last_error),
        retry_count,
      ) from last_error
  raise WorkflowManualReviewRequired(node_name, "NODE_MAX_RETRY", "unknown", MAX_NODE_RETRIES)


def _log_success(
  workflow_id: str,
  trace_id: str | None,
  node_name: str,
  agent_name: str | None,
  retry_count: int,
  cost_time_ms: int,
) -> None:
  logger.info(
    "trace_id=%s workflow_id=%s node_name=%s agent_name=%s retry_count=%s cost_time=%s status=SUCCESS",
    trace_id,
    workflow_id,
    node_name,
    agent_name,
    retry_count,
    cost_time_ms,
  )


def _log_failure(
  workflow_id: str,
  trace_id: str | None,
  node_name: str,
  agent_name: str | None,
  retry_count: int,
  cost_time_ms: int,
  error_code: str,
  error_msg: str,
) -> None:
  logger.error(
    "trace_id=%s workflow_id=%s node_name=%s agent_name=%s retry_count=%s cost_time=%s "
    "error_code=%s error_msg=%s",
    trace_id,
    workflow_id,
    node_name,
    agent_name,
    retry_count,
    cost_time_ms,
    error_code,
    error_msg,
  )
