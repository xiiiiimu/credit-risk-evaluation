# Python Agent

## Role

FastAPI sidecar (`credit-agent`, port 8090) that performs multi-step credit risk analysis. It does **not** make final approval decisions.

## Entry Points

| Endpoint | Purpose |
|----------|---------|
| `GET /health` | Liveness |
| `GET /v1/agents/health` | Per-agent circuit breaker status |
| `POST /v1/agents/credit/analyze` | Main analysis (requires `workflowId`) |

Auth: `X-Internal-Api-Key`, optional `X-Trace-Id`, `X-Session-Id`

## Integration Pattern

```
Java AgentRemoteClient ──HTTP──► Python /v1/agents/credit/analyze
Python nodes ──HTTP──► Java /internal/tools/invoke
Python credit_assessment ──MCP stdio──► credit-bureau server (mock)
```

- **Java → Python:** `AgentRemoteClient` (`credit.agent.base-url`)
- **Python → Java:** `SpringToolClient` (`SPRING_TOOL_BASE_URL`)
- Shared secret: `internal-api-key` (default `credit-agent-secret`)

## Workflow Runtime

Production path: `app/workflow/graph_runner.py` — sequential loop over `NODE_ORDER`.

LangGraph graph is defined in `app/graphs/credit_risk_ops_graph.py` but **not invoked at runtime**.

## Active Agents

| Node | Type | File | Role |
|------|------|------|------|
| `load_memory` | Tool | `memory_load_node.py` | User memory, credit history, product |
| `ocr_preprocess` | Tool | `ocr_preprocess_node.py` | OCR each document |
| `input_fusion` | Tool | `ocr_preprocess_node.py` | Fuse inputs → `unifiedRiskContext` |
| `document_review` | LLM | `document_review_agent.py` | Review materials, OCR cache |
| `document_verify` | Tool | `document_verify_node.py` | Java cross-check rules |
| `credit_assessment` | MCP + LLM | `credit_assessment_agent.py` | Bureau query + assessment |
| `anti_fraud` | Tool + LLM | `anti_fraud_agent.py` | Fraud signals + interpretation |
| `consensus` | Rules | `consensus_node.py` | Weighted voting |
| `suggestion_routing` | Pass-through | `suggestion_routing_node.py` | Propagate consensus |
| `final` | Builder | `final_response_node.py` | Human-readable answer |

Removed: `credit_advisory_agent.py` (deprecated — credit terms decided by Java).

## Consensus

Weights in `consensus_arbitrator.py`:
- Anti-fraud: 45%
- Credit assessment: 35%
- Document review: 20%

Rules:
- Both APPROVE and REJECT votes → `SUGGEST_MANUAL_REVIEW`
- `bureau_unavailable=true` → manual review
- `recent_apply_count_7d > 5` → risk level bumped

Vote values: `SUGGEST_APPROVE`, `SUGGEST_REJECT`, `SUGGEST_MANUAL`, `SUGGEST_MANUAL_REVIEW`

## MCP Integration

Client: `app/clients/mcp_credit_client.py`  
Server: `app/mcp_server/server.py` (run via `python -m app.mcp_server`)

| Tool | Data |
|------|------|
| `query_credit_record` | Bureau score, overdue, blacklist |
| `query_court_enforcement_record` | Enforcement count |
| `verify_income_stability` | Income verified, DTI |

Transport: `stdio` (default, spawns subprocess) or `inprocess` (tests)  
Timeout: `mcp_timeout_sec` default 3.0s → `McpTimeoutError` → manual review

Only `credit_assessment_agent` uses MCP. Demo data is mock, not real bureau API.

## LLM Pipeline

1. Load prompt via Java tool `get_prompt_config` (`app/prompts/loader.py`)
2. `invoke_llm()` with cache + rate limit (`app/nodes/common/chat_llm.py`)
3. `validate_or_repair_strict()` against Pydantic schemas (`app/schemas/`)
4. Failure → `SchemaValidationError` → node retry

Schemas: `anti_fraud.py`, `credit_assessment.py`, `document_review.py`

## Prompt & Rule Configuration

Agent pulls config from Java DB via tools:

| Tool | Returns |
|------|---------|
| `get_prompt_config` | `promptContent`, `version` |
| `get_rule_config` | `ruleContent`, `version` |
| `get_credit_product` | Product context |
| `get_product_rule_config` | Product rules |
| `get_product_material_requirements` | Material requirements |

Admin rollback:

```http
GET  /api/admin/config/prompt/{promptCode}/versions
POST /api/admin/config/prompt/{promptCode}/rollback/{version}
GET  /api/admin/config/rule/{ruleCode}/versions
POST /api/admin/config/rule/{ruleCode}/rollback/{version}
```

Fallback: built-in default prompts if DB fetch fails.

## Caching

Storage: Java-side Redis via tools `get/set_llm_cache`, `get/set_ocr_cache`

| Cache | Key | TTL |
|-------|-----|-----|
| LLM | `llm:result:{promptVersion}:{inputHash}` | 24h |
| OCR | `ocr:result:{fileMd5}` | 72h |

Config: `CACHE_ENABLED`, `credit.agent.cache.llm-ttl-hours`, `ocr-ttl-hours`

Cache hits recorded in audit with `cacheHit: true`, `token_count=0`.

## Resilience

| Mechanism | Location | Behavior |
|-----------|----------|----------|
| Node timeout | `resilience/timeout.py` | Default 15s per LLM agent node |
| Node retry | `workflow/retry.py` | 3×, backoff 2/4/8s |
| Agent circuit breaker | `resilience/circuit_breaker.py` | 5 failures → open 30s |
| Agent runtime guard | `resilience/agent_runtime.py` | Pre-check circuit + timeout |
| LLM rate limit | `resilience/llm_rate_limiter.py` | 60/min + 5 concurrent |
| Spring tool circuit | `spring_tool_client.py` | 5 failures → 30s open |
| MCP degradation | `credit_assessment_agent.py` | Timeout → `bureau_unavailable` |
| Java degradation | `CreditApplyAsyncProcessor` | `AgentUnavailableException` → manual review |

### Config defaults

| Config | Location | Default |
|--------|----------|---------|
| `AGENT_NODE_TIMEOUT_SEC` | Agent `.env` | 15 |
| `AGENT_CIRCUIT_FAILURE_THRESHOLD` | Agent `.env` | 5 |
| `LLM_RATE_LIMIT_PER_MINUTE` | Agent `.env` | 60 |
| `credit.agent.health-check-interval-ms` | `application.yml` | 10000 |
| `credit.agent.circuit-half-open-max-probes` | `application.yml` | 1 |

### Health APIs

```http
GET /v1/agents/health          # Python — per sub-agent status
GET /api/admin/agent/health    # Java admin aggregate (login required)
```

## Key Tools Used by Agent

| Category | Tools |
|----------|-------|
| Memory / product | `get_user_memory`, `get_user_credit_history`, `get_credit_product`, ... |
| OCR / fusion | `recognize_document`, `fuse_application_input` |
| Verification | `verify_application_documents`, `evaluate_fraud_signals` |
| Workflow | `resolve_workflow_idempotent`, `acquire_workflow_execution`, ... |
| Cache | `get_llm_cache`, `set_llm_cache`, `get_ocr_cache`, `set_ocr_cache` |
| Observability | `save_audit_log`, `save_credit_workflow_trace`, `append_workflow_trace` |

Full registry: `ToolRegistry.java` (~47 tools)

## Testing

```bash
cd credit-agent && pytest tests/test_prompt_loader_and_strict_validation.py -q
cd credit-agent && pytest tests/test_resilience_phase3.py -q
cd credit-agent && pytest tests/test_llm_cache.py -q
cd credit-agent && pytest tests/test_input_fusion.py tests/test_ocr_preprocess.py -q
cd credit-agent && pytest tests/test_architecture_refactor.py -q
```

### Manual tests

**Circuit breaker:** fail same agent 5× → next call → `MANUAL_REVIEW`  
**Timeout:** set `AGENT_NODE_TIMEOUT_SEC=1`, submit complex application  
**Rate limit:** set `LLM_RATE_LIMIT_MAX_CONCURRENT=1`, concurrent submits
