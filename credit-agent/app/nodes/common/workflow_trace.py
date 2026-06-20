import inspect
import logging
from functools import wraps
from typing import Any, Callable, TypeVar

logger = logging.getLogger(__name__)

F = TypeVar("F", bound=Callable[..., Any])


def _wrap_sync(func: F) -> F:
    @wraps(func)
    def sync_wrapper(*args: Any, **kwargs: Any) -> Any:
        logger.info("Enter node: %s", func.__name__)
        try:
            result = func(*args, **kwargs)
            logger.info("Exit node: %s", func.__name__)
            return result
        except Exception:
            logger.exception("Node failed: %s", func.__name__)
            raise

    return sync_wrapper  # type: ignore[return-value]


def _wrap_async(func: F) -> F:
    @wraps(func)
    async def async_wrapper(*args: Any, **kwargs: Any) -> Any:
        logger.info("Enter node: %s", func.__name__)
        try:
            result = await func(*args, **kwargs)
            logger.info("Exit node: %s", func.__name__)
            return result
        except Exception:
            logger.exception("Node failed: %s", func.__name__)
            raise

    return async_wrapper  # type: ignore[return-value]


def trace_node(
    tool_client: Any = None,
    workflow_id: Any = None,
    trace_id: Any = None,
    node_name: str | None = None,
    phase: str | None = None,
    **extra: Any,
) -> Any:
    """
    Lightweight workflow tracing.

    Decorator usage::
        @trace_node
        def my_node(...): ...

    Inline usage (current graph nodes)::
        trace_node(tool_client, workflow_id, trace_id, "NodeName", "start")
    """
    if callable(tool_client) and node_name is None and phase is None and workflow_id is None:
        if inspect.iscoroutinefunction(tool_client):
            return _wrap_async(tool_client)
        return _wrap_sync(tool_client)

    parts = [f"node={node_name}", f"phase={phase}"]
    if workflow_id is not None:
        parts.append(f"workflow_id={workflow_id}")
    if trace_id is not None:
        parts.append(f"trace_id={trace_id}")
    for key, value in extra.items():
        parts.append(f"{key}={value}")
    logger.info("workflow trace %s", " ".join(parts))
    return None
