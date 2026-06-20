# Workflow Persistence

## Overview

Each credit application gets a `workflowId` (UUID). The Python Agent runs a 10-node sequential pipeline; Java persists state in MySQL for audit, idempotency, and checkpoint resume.

## Database Tables

| Table | Purpose |
|-------|---------|
| `tb_workflow` | Main state: `status`, `current_node`, `retry_count`, `result_json`, links to `taskId` / `applicationId` |
| `tb_workflow_node` | Per-node execution: input/output, error, retry count, duration |
| `tb_workflow_checkpoint` | State snapshot (`state_json`, history) for resume |

### Workflow status lifecycle

```
INIT → RUNNING → SUCCESS | FAILED | MANUAL_REVIEW
```

### Task status lifecycle (Java async task)

```
PENDING → MQ_SENT | MQ_SEND_FAILED → RUNNING → SUCCESS | FAILED | MANUAL_REVIEW
```

### Application status lifecycle

```
ANALYZING (draft) → PENDING_DECISION → APPROVED | REJECTED | MANUAL_REVIEW
```

## Node Pipeline

```
load_memory
→ ocr_preprocess
→ input_fusion
→ document_review      (LLM)
→ document_verify      (Java tool)
→ credit_assessment    (MCP + LLM)
→ anti_fraud           (Java tool + LLM)
→ consensus            (weighted rules)
→ suggestion_routing
→ final
```

Implementation: `credit-agent/app/workflow/graph_runner.py`  
Node order: `credit-agent/app/workflow/constants.py`

## Core Capabilities

### Node persistence

Each node calls Java tools:
- `begin_workflow_node` — record start
- `complete_workflow_node` — record output, duration
- `save_workflow_checkpoint` — save state after success

### Checkpoint & resume

1. Kill Agent mid-run
2. Check `tb_workflow_checkpoint.current_node`
3. Re-call analyze with same `workflowId` → resumes from next node

Resume logic: `_resume_start_index()` in `graph_runner.py`

### Retry

- Max 3 retries per node
- Backoff: 2s → 4s → 8s
- Exceeded → `WorkflowManualReviewRequired` → workflow `MANUAL_REVIEW`

Implementation: `credit-agent/app/workflow/retry.py`

### CAS acquire (duplicate execution guard)

```sql
UPDATE tb_workflow SET status='RUNNING' WHERE status IN ('INIT','FAILED')
```

Combined with Redis lock `workflow:lock:{workflowId}` (TTL 5 min).

## APIs

```http
GET /api/admin/credit/workflow/{workflowId}
```

Task poll includes workflow execution info:

```http
GET /api/credit/apply/task/{taskId}
```

Response field: `workflowExecution`

## Input Fusion & OCR (workflow context)

| Module | Description |
|--------|-------------|
| `InputFusionService` | Merges structuredApplication / userNarrative / ocrDocuments → `unifiedRiskContext` |
| `MockOcrService` | Default OCR; `TencentOcrService` / `AliyunOcrService` interfaces reserved |
| `ocr_preprocess` node | OCR + quality gate (blur/copy/screenshot/tamper → `MANUAL_REVIEW`) |
| `input_fusion` node | Tool `fuse_application_input` |

### Backward compatibility

- Legacy clients sending only `content` field still work (OCR skipped when no documents)
- Optional fields: `income`, `occupation`, `documents[]`, etc.

## Workflow Tools (Java)

| Tool | Description |
|------|-------------|
| `resolve_workflow_idempotent` | Check RETURN_RESULT / WAIT / RUN |
| `acquire_workflow_execution` | Lua lock + CAS |
| `release_workflow_lock` | Release lock |
| `start_workflow` | Transition to RUNNING |
| `begin_workflow_node` / `complete_workflow_node` | Node lifecycle |
| `save_workflow_checkpoint` / `load_workflow_checkpoint` | Checkpoint I/O |
| `finish_workflow` | Final status + resultJson |

## Testing

### Idempotency

```bash
curl -X POST http://127.0.0.1:8090/v1/agents/credit/analyze \
  -H "Content-Type: application/json" \
  -H "X-Internal-Api-Key: credit-agent-secret" \
  -d '{"userId":1,"productId":1,"applyAmount":50000,"content":"...","workflowId":"wf-idem-demo"}'
```

Second call with same `workflowId` should return cached result (no LLM calls).

### Retry / manual review

Inject `raise RuntimeError` in a node → 3 retries → `tb_workflow.status=MANUAL_REVIEW`.

### Unit tests

```bash
cd credit-risk-platform && mvn test -Dtest=WorkflowIdempotencyServiceTest
cd credit-agent && pytest tests/test_workflow_retry.py tests/test_workflow_graph_runner.py -q
cd credit-agent && pytest tests/test_e2e_stability_integration.py -q
```

### E2E / stability tests (Java)

| Test class | Coverage |
|------------|----------|
| `CreditApplyPipelineE2ETest` | Mock Agent → rule engine (low/medium/high risk, OCR low confidence) |
| `CreditApplyAsyncProcessorE2ETest` | Async task chain, workflowId idempotency |
| `WorkflowExecutionStabilityTest` | Idempotency, lock expiry, CAS failure |
| `ProductRuleVersionGrayTest` | Product rule V1/V2 rollback |
| `RuleConfigSafeDefaultGrayTest` | High risk cannot default to approve |

```bash
cd credit-risk-platform && mvn test -Dtest="*E2ETest,*GrayTest,*StabilityTest"
```
