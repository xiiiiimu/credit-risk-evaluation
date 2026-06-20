# Observability

## Audit Log

Table: `tb_audit_log`

Written by Python Agent via tool `save_audit_log` and Java MQ audit service.

| Field | Description |
|-------|-------------|
| `workflow_id` | Workflow identifier |
| `node_name` | Agent node |
| `prompt_version` | Prompt config version used |
| `token_count` | LLM tokens consumed |
| `cost_time_ms` | Call duration |
| `cache_hit` | Whether LLM/OCR cache was hit |
| request/response | Call payload (may contain sensitive data) |

Migration: `db/migration/V003_audit_log.sql`, `V004_cache_hit.sql`

## Admin APIs

```http
GET /api/admin/audit/workflow/{workflowId}
GET /api/admin/metrics/summary
GET /api/admin/credit/workflow/{workflowId}
GET /api/admin/credit/trace/{applicationId}
```

Task poll (user-facing) includes workflow info:

```http
GET /api/credit/apply/task/{taskId}
```

## Micrometer Metrics

Exposed at `/actuator/metrics` (health + metrics enabled in `application.yml`):

| Metric | Description |
|--------|-------------|
| `agent.node.invoke` | Node success/failure rate |
| `agent.node.retry` | Node retry count |
| `agent.llm.tokens` | Token consumption |
| `agent.llm.invoke` | LLM call count |
| `agent.llm.cache_hit` | LLM cache hits |
| `workflow.manual_review` | Manual review count |
| `agent.remote.call` | Java → Python Agent calls |

Example:

```http
GET /actuator/metrics/agent.llm.tokens
```

## MQ Audit Events

`CreditApprovalMqAuditService` events in `tb_audit_log`:

- `MQ_SEND_SUCCESS` / `MQ_SEND_FAILED`
- `MQ_CONSUME_START` / `SUCCESS` / `FAILED` / `SKIP`
- `MQ_RETRY`
- `MQ_DEAD_LETTER`

## Workflow Trace Tables

| Table | Content |
|-------|---------|
| `tb_workflow_node` | Per-node input/output, retry, duration |
| `tb_agent_workflow_trace` | Agent ReAct-style trace (thought/action/observation) |
| `tb_credit_workflow_trace` | Platform-side workflow trace |
| `tb_agent_task_log` | Tool/remote call logs (async via `@Async`) |

Trace fields: `trace_id`, `workflow_id`, `node_name`, `agent_name`, `retry_count`, `cost_time`, `error_code`

## Health Checks

| Endpoint | Scope |
|----------|-------|
| `GET /health` | Python liveness |
| `GET /v1/agents/health` | Python per-agent circuit state |
| `GET /api/admin/agent/health` | Java aggregated view |
| `GET /internal/health` | Java internal tool gateway |
| `GET /actuator/health` | Spring Boot health |

Agent states: `UP` / `DOWN` / `DEGRADED`

## Agent Audit Recorder

Python: `app/audit/recorder.py`  
All non-cache LLM/tool calls persisted via Java `save_audit_log`.

## Testing

```bash
cd credit-risk-platform && mvn test -Dtest=AuditLogServiceTest
cd credit-agent && pytest tests/test_audit_recorder.py -q
```

After submitting an application:

```http
GET /api/admin/audit/workflow/{workflowId}
GET /actuator/metrics/agent.llm.tokens
```

## Production Recommendations

- Add request/response masking in audit logs
- Export Micrometer to Prometheus + Grafana (roadmap item)
- Set up alerts on `workflow.manual_review` spike and `MQ_DEAD_LETTER` events
