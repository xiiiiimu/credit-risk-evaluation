# Performance Testing

## JMeter Setup

Script: `credit-approval-submit-test.jmx`  
Target endpoint: `POST /api/credit/apply/submit`

Test flow includes login + submit (1000 submit samples in benchmark runs).

Reports:
- MQ mode: `jmeter-results/`
- Direct mode: `jmeter-results-direct/`

## Results Summary

1000 concurrent submit requests, **0% HTTP error rate** in both modes.

| Mode | Config | Avg RT | P95 | Throughput | Response status |
|------|--------|--------|-----|------------|-----------------|
| **MQ** | `credit.mq.enabled=true` | 2543 ms | 6670 ms | 18.10 req/s | `MQ_SENT` |
| **Direct** | `credit.mq.enabled=false` | 290 ms | 481 ms | 96.99 req/s | `PENDING` |

Additional stats (MQ mode):
- Median: ~1933 ms
- P99: ~9459 ms
- Min / Max: 317 ms / 13704 ms

Additional stats (Direct mode):
- Median: ~281 ms
- P99: ~547 ms
- Min / Max: 88 ms / 673 ms

## Interpretation

### Direct mode (local thread pool)

- Uses `DirectCreditApprovalTaskTrigger` → `taskExecutor` (core 4, max 8, queue 200)
- Submit returns immediately after task insert — no RocketMQ `syncSend`
- **Pros:** Low submit latency (~290 ms avg), high throughput (~97 req/s)
- **Cons:** No broker persistence; task lost if JVM crashes; no cross-instance load balancing; limited retry semantics

### MQ mode (production default)

- Submit returns after task/workflow/outbox DB commit — RocketMQ send is async via `MqOutboxPublisher`
- All samples returned `MQ_SENT` — broker delivery confirmed
- **Pros:** Durable dispatch, consumer retry, DLQ, audit trail, horizontal scaling
- **Cons:** Higher submit RT (~2543 ms avg) due to sync send confirmation and broker round-trip

### Key takeaway

The RT difference reflects **what submit synchronously waits for**:
- Direct: DB insert only
- MQ: DB insert + message delivery confirmation

End-to-end approval time (Agent + rule engine) is similar in both modes — only the submit path differs.

## Comparison File

Side-by-side CSV: `jmeter-results-direct/comparison.csv`

Text summary: `jmeter-results/summary.txt`, `jmeter-results-direct/summary.txt`

HTML reports: `jmeter-results/html-report/`, `jmeter-results-direct/html-report/`

## Reproduce

1. Start full stack (including RocketMQ for MQ mode)
2. Configure JMeter with base URL `http://127.0.0.1:8082`
3. For Direct mode: set `credit.mq.enabled=false` in `application.yml` and restart backend
4. Run `credit-approval-submit-test.jmx`
5. Compare aggregate reports

## Optimization Roadmap (submit RT)

- Upgrade polling outbox metrics/alerting; optional binlog/CDC outbox at scale
- Separate "accept" and "dispatch" phases in API response design
