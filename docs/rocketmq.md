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

## Producer Flow (MySQL Polling Outbox)

Submit 路径**不在** HTTP 事务里直接 `syncSend`。

1. `CreditApplySubmissionTxService`（`@Transactional`）同一本地事务：
   - insert `CreditAsyncTask`（PENDING）
   - init workflow（INIT）
   - insert `tb_mq_outbox_event`（NEW，payload = `CreditApprovalTaskMessage` JSON）
2. HTTP 返回 `taskId`（此时 task 可能仍为 PENDING）
3. `MqOutboxPublisher`（每 1s 轮询）：
   - CAS `NEW/FAILED` 或**超时 SENDING**（`update_time` 超过 2 分钟）→ `SENDING`
   - `CreditApprovalTaskProducer.syncSend(destination, message, timeout)`
   - 成功 → outbox `SENT`，task `MQ_SENT`，audit `MQ_SEND_SUCCESS`
   - 失败 → outbox `FAILED` + 指数退避（5s / 30s / 2min / 5min）；超过 5 次 → task `MQ_SEND_FAILED`

> **SENDING 超时恢复**：Publisher 在发送过程中宕机时，事件可能卡在 SENDING；扫描会重新捞起。重复投递仍可能发生，Consumer 幂等必须保留。

> Polling Outbox 是当前实现；后续生产化可替换为 binlog/CDC Outbox 或 RocketMQ 事务消息。

Admin 补发：重置 outbox event 为 `NEW`，**不绕过 outbox 直接发 MQ**。

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
| Trigger | Outbox → `MqOutboxPublisher` | `DirectCreditApprovalTaskTrigger` |
| Executor | RocketMQ consumer threads | `taskExecutor` (core 4, max 8, queue 200) |
| Durability | Outbox + Broker-persisted | In-process only |
| Retry | Outbox backoff + RocketMQ reconsume + DLQ | Limited |
| Submit RT | Lower（HTTP 仅落库 + outbox，不阻塞 syncSend） | Lower |
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
- MySQL Polling Outbox：task/workflow/outbox 同事务落库
- `MqOutboxPublisher` 异步 `syncSend`；失败指数退避，超限 `MQ_SEND_FAILED`
- SENDING 超时（2 分钟）自动恢复
- Admin 重置 outbox 补发

### Consume-side
- At-least-once delivery（Outbox 不能消除重复投递）
- Terminal task guard
- Workflow lock + idempotency
- 16 reconsume → DLQ → manual review

### Future improvements
- Scheduled auto-redelivery job for `MQ_SEND_FAILED`
- Upgrade polling outbox to binlog/CDC for production scale
- Automatic DLQ reprocessing (DLQ only marks manual review today)

## Why RocketMQ over RabbitMQ / Kafka

**vs RabbitMQ:** Simpler for task-queue pattern; built-in delayed retry and DLQ; common in Java/Spring China ecosystem.

**vs Kafka:** Lower throughput requirement; no need for log-stream semantics. RocketMQ provides retry/DLG/tag filtering out of the box for this use case.
