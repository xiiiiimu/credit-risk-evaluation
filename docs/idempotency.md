# Idempotency & Duplicate Handling

## Three Layers

### Layer 1 — HTTP Submit

**Scope:** `credit.apply.submit`  
**Key:** `Idempotency-Key` header or body field `idempotencyKey`

Flow (`IdempotencyService` + `CreditApplyAsyncService.submitAsync()`):

1. **无 Idempotency-Key** → 直接走普通提交流程。
2. **有 Idempotency-Key**：
   - Redis `SETNX idempotent:credit.apply.submit:{key}`（TTL 24h）
   - **抢锁成功** → 写入 `tb_agent_idempotent_record`（`status=PROCESSING`）→ 创建 task/workflow/outbox → 保存 `{taskId}` 到 `responseJson`（`status=SUCCESS`）
   - **抢锁失败** → 只读 MySQL 幂等记录，返回相同 `taskId`；**不再执行 supplier**
   - 若抢锁失败但 `responseJson` 尚未写入 → 每 100ms 轮询，最多 3s；超时抛出 `same idempotency key is processing, retry later`
   - 同一 key 携带不同请求体（`request_hash` 不一致）→ 抛出 `IdempotencyConflictException`
   - MySQL `(scope, idempotency_key)` 唯一索引 + `DuplicateKeyException` 兜底

Implementation: `CreditApplyAsyncService.submitAsync()` — **不再**直接查 `tb_credit_async_task` 做幂等。

### Layer 2 — Workflow State

**Key:** `workflowId`

`WorkflowIdempotencyService.resolve()`:

| Status | Action | Behavior |
|--------|--------|----------|
| `SUCCESS` + `resultJson` | `RETURN_RESULT` | Skip Agent, use cached result |
| `RUNNING` / `PENDING` | `WAIT` | MQ retry or HTTP 409 |
| `INIT` / missing | `RUN` | Proceed |

Checked on both Java (`WorkflowExecutionService`) and Python (`resolve_workflow_idempotent` tool).

### Layer 3 — Execution Mutex

`WorkflowExecutionService.acquireForExecution()`:
1. Idempotency check (layer 2)
2. Redis lock: `workflow:lock:{workflowId}` via Lua SETNX, TTL 5 min
3. DB CAS: `UPDATE status=RUNNING WHERE status IN (INIT, FAILED)`

Only one instance executes; others get `WAIT`.

Lock release in `finally` block after Agent call + commit.

---

## Duplicate MQ Consumption

RocketMQ delivers at-least-once. Handling:

| Guard | Location | Behavior |
|-------|----------|----------|
| Terminal skip | `CreditApprovalTaskGuardService` | `SUCCESS`/`MANUAL_REVIEW`/`FAILED` → ack, no reprocess |
| In-flight detect | `CreditApprovalTaskConsumer` | RUNNING + WAIT → `CreditApprovalTaskRetryableException` |
| Lock + CAS | `WorkflowExecutionService` | Second consumer cannot acquire |
| Result cache | Workflow SUCCESS | Processor uses `resultJson`, skips Agent call |
| DLQ idempotency | `CreditApprovalTaskDlqConsumer` | Skip if already terminal |

---

## Message Loss Handling

### Send-side loss
- **Transactional Outbox**（`tb_mq_outbox_event`）：task/workflow/outbox 同事务落库，MQ 由 `MqOutboxPublisher` 异步发送
- Outbox 发送失败 → 指数退避重试（最多 5 次）→ `MQ_SEND_FAILED`
- Admin: `POST /api/admin/credit/mq/redelivery/{taskId}` → 重置 outbox 为 `NEW`，由 publisher 统一补发

### Consume-side loss
- Retryable exceptions → RocketMQ reconsume (max 16)
- Exceeded → DLQ → `MANUAL_REVIEW`

### Agent call loss
- Java retry + circuit breaker on `AgentHttpExecutor`
- Unavailable → synthetic manual-review response

---

## Known Gap: Commit Idempotency

`CreditApplyWorkflowService.commit()` has no independent business idempotency key.

Normal path: lock + workflow `SUCCESS` with cached `resultJson` prevents duplicate Agent calls.

Edge case: lock expires before commit completes, workflow not yet `SUCCESS` — theoretical duplicate side effects (e.g. double grant).

**Mitigation today:** 5-min lock TTL vs typical <60s Agent runtime.  
**Recommended:** add commit version or idempotency record on grant/limit operations.

---

## Redis + MySQL Dual Layer (Submit)

Why both?
- Redis: fast concurrent dedup
- MySQL: survives Redis eviction/expiry

---

## Testing

```bash
# Same Idempotency-Key → same taskId
curl -X POST http://127.0.0.1:8082/api/credit/apply/submit \
  -H "Idempotency-Key: test-key-001" \
  ...

# Same workflowId → cached Agent result
curl -X POST http://127.0.0.1:8090/v1/agents/credit/analyze \
  -H "X-Internal-Api-Key: credit-agent-secret" \
  -d '{"workflowId":"wf-idem-demo", ...}'

cd credit-risk-platform && mvn test -Dtest=WorkflowIdempotencyServiceTest
cd credit-risk-platform && mvn test -Dtest=WorkflowExecutionStabilityTest
```

---

## Scenario Matrix

| Scenario | Behavior |
|----------|----------|
| Duplicate submit (same key) | Return existing taskId |
| Duplicate analyze (SUCCESS workflow) | Return cached resultJson |
| Duplicate analyze (RUNNING) | HTTP 409 / MQ WAIT retry |
| Duplicate MQ consume (terminal task) | Skip, audit CONSUME_SKIP |
| Duplicate MQ consume (in-flight) | RetryableException |
| Worker crash mid-run | Lock expires; checkpoint resume; MQ reconsume |
