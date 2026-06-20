# RocketMQ Async Approval

## Role in the System

RocketMQ decouples HTTP submit from Agent execution. Production default: `credit.mq.enabled=true`.

When disabled, `DirectCreditApprovalTaskTrigger` falls back to local `ThreadPoolTaskExecutor` — useful for local dev without RocketMQ.

## Configuration

From `application.yml` / `RocketMqApprovalProperties`:

| Setting | Value |
|---------|-------|
| Topic | `credit-approval-task-topic` |
| Tag | `credit-apply` |
| Producer group | `credit-approval-producer` |
| Consumer group | `credit-approval-task-consumer` |
| DLQ topic | `%DLQ%credit-approval-task-consumer` |
| DLQ consumer group | `credit-approval-task-dlq-consumer` |
| Producer retry | 2 |
| Send timeout | 3000 ms |
| Consumer max reconsume | 16 |
| Consumer threads max | 16 |

Docker Compose includes `credit-rmqnamesrv`, `credit-rmqbroker`, and `credit-rmq-init` (topic auto-create).

## Message Design

Payload (`CreditApprovalTaskMessage`) — index only, not full application data:

```
taskId, workflowId, applicationId, userId, productId,
idempotencyKey, traceId, createdAt
```

Consumer reloads full context from `tb_credit_async_task` in MySQL.

**Rationale:** DB is source of truth; small messages avoid stale payload issues.

## Producer Flow

`CreditApprovalTaskProducer.syncSend()`:
1. Send with timeout + retry
2. Success → task status `MQ_SENT`, audit `MQ_SEND_SUCCESS`
3. Failure → task status `MQ_SEND_FAILED`, audit `MQ_SEND_FAILED`

## Consumer Flow

`CreditApprovalTaskConsumer.onMessage()`:
1. Restore `traceId` to MDC
2. Load task by `taskId`
3. Skip if terminal status (`SUCCESS`, `MANUAL_REVIEW`, `FAILED`)
4. If in-flight duplicate (RUNNING + workflow WAIT) → throw `CreditApprovalTaskRetryableException`
5. Call `CreditApplyAsyncProcessor.processTask()`
6. Audit: `MQ_CONSUME_START` / `SUCCESS` / `FAILED` / `SKIP`

## DLQ Consumer

`CreditApprovalTaskDlqConsumer`:
- Triggered after 16 failed reconsumes
- Marks task `MANUAL_REVIEW` with error: "MQ 消费超过最大重试次数，转人工复核"
- Audit: `MQ_DEAD_LETTER`
- Skips if task already terminal

## Retryable Exceptions

Consumer throws `CreditApprovalTaskRetryableException` for:
- Workflow in progress (`WAIT`)
- Lock acquisition failure
- Agent network errors (`ResourceAccessException`)

RocketMQ handles delayed redelivery.

## Compensation

`CreditApprovalTaskRedeliveryService` + admin API:

```http
POST /api/admin/credit/mq/redelivery/{taskId}
```

Eligible statuses: `MQ_SEND_FAILED`, `FAILED`, `MQ_SENT`, `PENDING`.

> **Gap:** No scheduled auto-scan for `MQ_SEND_FAILED` — manual or admin API only.

## MQ vs Direct Mode

| Aspect | MQ mode | Direct mode |
|--------|---------|-------------|
| Trigger | `MqCreditApprovalTaskTrigger` | `DirectCreditApprovalTaskTrigger` |
| Executor | RocketMQ consumer threads | `taskExecutor` (core 4, max 8, queue 200) |
| Durability | Broker-persisted | In-process only |
| Retry | RocketMQ reconsume + DLQ | Limited |
| Submit RT | Higher (includes syncSend) | Lower |
| Use case | Production / reliable async | Local dev / tests |

See [performance.md](performance.md) for JMeter benchmarks.

## Audit Events

`CreditApprovalMqAuditService` → `tb_audit_log`:

| Event | Meaning |
|-------|---------|
| `MQ_SEND_SUCCESS` / `MQ_SEND_FAILED` | Producer result |
| `MQ_CONSUME_START` / `SUCCESS` / `FAILED` / `SKIP` | Consumer result |
| `MQ_RETRY` | Reconsume triggered |
| `MQ_DEAD_LETTER` | DLQ received |

## Reliability Patterns

### Send-side
- `syncSend` with retry
- Failed sends persisted as `MQ_SEND_FAILED` (not silent loss)
- Admin redelivery API

### Consume-side
- At-least-once delivery
- Terminal task guard
- Workflow lock + idempotency
- 16 reconsume → DLQ → manual review

### Not implemented
- Transactional Outbox (DB insert + MQ send not atomic)
- Scheduled auto-redelivery job
- Automatic DLQ reprocessing (DLQ only marks manual review)

## Why RocketMQ over RabbitMQ / Kafka

**vs RabbitMQ:** Simpler for task-queue pattern; built-in delayed retry and DLQ; common in Java/Spring China ecosystem.

**vs Kafka:** Lower throughput requirement; no need for log-stream semantics. RocketMQ provides retry/DLG/tag filtering out of the box for this use case.
