# Deployment Guide

## Prerequisites

| Dependency | Version | Notes |
|------------|---------|-------|
| JDK | 8+ | Backend |
| Maven | 3.6+ | Backend build |
| Python | 3.10+ | Agent |
| Node.js | 18+ | Frontend dev |
| MySQL | 5.7+ | Port 3307 in local example |
| Redis | 7+ | Port 6379 |
| RocketMQ | 4.9+ | NameServer 9876 (production default) |
| DeepSeek API Key | — | Or OpenAI-compatible endpoint |

---

## Docker Compose

From project root:

```bash
export OPENAI_API_KEY=your-deepseek-key
docker compose up --build
```

### Services

| Container | Image / Build | Port |
|-----------|---------------|------|
| `credit-mysql` | mysql:5.7 | 3307 |
| `credit-redis` | redis:7-alpine | 6379 |
| `credit-rmqnamesrv` | apache/rocketmq:4.9.7 | 9876 |
| `credit-rmqbroker` | apache/rocketmq:4.9.7 | 10909, 10911 |
| `credit-rmq-init` | topic init script | — |
| `credit-backend` | `./credit-risk-platform` | 8082 |
| `credit-agent` | `./credit-agent` | 8090 |
| `credit-web` | `./credit-risk-web` | 80 |

### Database initialization

Compose does not auto-import SQL. After first start:

```bash
docker exec -i credit-mysql mysql -uroot credit < credit-risk-platform/src/main/resources/db/credit_schema.sql
docker exec -i credit-mysql mysql -uroot credit < credit-risk-platform/src/main/resources/db/credit_seed.sql

for f in V001 V002 V003 V004 V005 V006; do
  docker exec -i credit-mysql mysql -uroot credit < credit-risk-platform/src/main/resources/db/migration/${f}_*.sql
done
```

Or run migrations individually — see files under `db/migration/`.

---

## Local Development

### 1. MySQL

```bash
mysql -u root -p < credit-risk-platform/src/main/resources/db/credit_schema.sql
mysql -u root -p credit < credit-risk-platform/src/main/resources/db/credit_seed.sql
mysql -u root -p credit < credit-risk-platform/src/main/resources/db/migration/V001_workflow_persistence.sql
mysql -u root -p credit < credit-risk-platform/src/main/resources/db/migration/V002_prompt_rule_config.sql
mysql -u root -p credit < credit-risk-platform/src/main/resources/db/migration/V003_audit_log.sql
mysql -u root -p credit < credit-risk-platform/src/main/resources/db/migration/V004_cache_hit.sql
mysql -u root -p credit < credit-risk-platform/src/main/resources/db/migration/V005_input_fusion_workflow_init.sql
mysql -u root -p credit < credit-risk-platform/src/main/resources/db/migration/V006_product_dynamic_config.sql
```

Default connection: `application.yml` → `127.0.0.1:3307`, database `credit`

### 2. Redis

Ensure `127.0.0.1:6379` is reachable.

### 3. RocketMQ (MQ mode)

Start NameServer + Broker, or use Docker Compose MQ services only:

```bash
docker compose up credit-rmqnamesrv credit-rmqbroker credit-rmq-init
```

Set in `application.yml`:
```yaml
credit:
  mq:
    enabled: true
rocketmq:
  name-server: 127.0.0.1:9876
```

To skip RocketMQ locally:
```yaml
credit:
  mq:
    enabled: false
```

### 4. Spring Boot Backend

```bash
cd credit-risk-platform
mvn spring-boot:run
```

→ `http://127.0.0.1:8082`

### 5. Python Agent

```bash
cd credit-agent
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# Edit .env — set OPENAI_API_KEY
uvicorn app.main:app --host 0.0.0.0 --port 8090
```

→ `http://127.0.0.1:8090`

### 6. Frontend

```bash
cd credit-risk-web
npm install
npm run dev
```

→ `http://localhost:5173` (dev proxy `/api` → 8082)

---

## Configuration Reference

| Config | Location | Description |
|--------|----------|-------------|
| `OPENAI_API_KEY` | `credit-agent/.env` | DeepSeek API key — **do not commit** |
| `SPRING_TOOL_BASE_URL` | `credit-agent/.env` | Java tool gateway (default `http://127.0.0.1:8082`) |
| `INTERNAL_API_KEY` | Agent `.env` | Must match Java `credit.agent.internal-api-key` |
| `credit.agent.base-url` | `application.yml` | Java → Agent URL |
| `credit.agent.internal-api-key` | `application.yml` | Bidirectional internal auth |
| `credit.mq.enabled` | `application.yml` | MQ vs Direct trigger |
| `spring.datasource.*` | `application.yml` | MySQL connection |
| `spring.redis.*` | `application.yml` | Redis connection |
| `credit.agent.cache.*` | `application.yml` | LLM/OCR cache TTL |
| `credit.agent.ocr.provider` | `application.yml` | `mock` / `tencent` / `aliyun` |

### Agent `.env` resilience defaults

```
AGENT_NODE_TIMEOUT_SEC=15
AGENT_CIRCUIT_FAILURE_THRESHOLD=5
LLM_RATE_LIMIT_PER_MINUTE=60
LLM_RATE_LIMIT_MAX_CONCURRENT=5
CACHE_ENABLED=true
```

---

## Demo Accounts

Seed data in `credit_seed.sql`:
- Phone: `13800000001` (and others)
- Dev mode: `credit.dev.expose-login-code=true` returns SMS code in API response

### Demo flow

1. Login at `/login`
2. Submit at `/apply` → receive `taskId`
3. Poll `/tasks/:taskId`
4. View results at `/applications`
5. Admin review at `/admin/reviews` (admin user IDs in `credit.agent.admin-user-ids`)

---

## Security Notes

- Never commit `.env` or real API keys
- Replace default `credit-agent-secret` in production
- Use environment variables for DB passwords (not hardcoded in `application.yml`)
- Audit logs may contain PII — add masking before production
- MCP bureau data is mock — replace with real API + proper credentials in production

---

## Troubleshooting

| Issue | Check |
|-------|-------|
| Submit returns `MQ_SEND_FAILED` | RocketMQ running? NameServer address correct? |
| Task stuck `PENDING` | Consumer running? Check `credit.mq.enabled` |
| Agent 503 | `OPENAI_API_KEY` set? |
| Agent 409 | Same workflowId still RUNNING — wait or check lock |
| DB connection refused | MySQL port 3307 vs 3306 in Docker |
| Frontend 502 | Backend 8082 up? Nginx proxy config in Docker |

Manual MQ redelivery:

```http
POST /api/admin/credit/mq/redelivery/{taskId}
```
