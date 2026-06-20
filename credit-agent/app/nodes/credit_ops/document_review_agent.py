import json
from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage

from app.audit.recorder import AuditContext, record_audit
from app.cache.result_cache import content_md5, get_ocr_cache, set_ocr_cache
from app.clients.spring_tool_client import SpringToolClient
from app.config import Settings
from app.nodes.common.chat_llm import chat_llm, invoke_llm
from app.nodes.common.schema_validator import validate_or_repair_strict
from app.nodes.common.workflow_trace import trace_node
from app.nodes.credit_ops.credit_workflow_trace import NodeTimer, trace_credit_node
from app.prompts.loader import load_prompt_meta
from app.schemas.document_review import DocumentReviewSchema
from app.state.credit_ops_state import CreditOpsState


def document_review_agent(
    state: CreditOpsState, settings: Settings, tool_client: SpringToolClient
) -> dict[str, Any]:
    wf = state.get("workflow_id")
    tid = state.get("trace_id")
    timer = NodeTimer()
    trace_node(tool_client, wf, tid, "DocumentReviewAgent", "start")

    llm = chat_llm(settings)
    prompt, prompt_version = load_prompt_meta(tool_client, "document_review", tid)
    audit = AuditContext(
        workflow_id=wf,
        trace_id=tid,
        node_name="document_review",
        prompt_code="document_review",
        prompt_version=prompt_version,
    )

    content = state.get("content") or ""
    file_md5 = content_md5(content)
    if settings.cache_enabled:
        cached_doc = get_ocr_cache(tool_client, file_md5, trace_id=tid)
        if cached_doc:
            review_json = cached_doc.get("document_review_json") or {}
            review_result = cached_doc.get("document_review_result")
            doc_complete = bool(review_json.get("docComplete"))
            summary = str(review_json.get("summary") or "")
            ticket_title = str(cached_doc.get("ticket_title") or "信贷资料复核")
            ticket_desc = str(cached_doc.get("ticket_description") or content)
            confidence = float(review_json.get("confidence") or 0.0)
            missing = list(review_json.get("missingDocs") or [])
            record_audit(
                tool_client,
                call_type="LLM",
                audit=audit,
                request=f"ocr:result:{file_md5}",
                response=json.dumps(review_json, ensure_ascii=False),
                token_count=0,
                cost_time_ms=0,
                success=True,
                cache_hit=True,
            )
            trace_node(tool_client, wf, tid, "DocumentReviewAgent", "end", decision=str(doc_complete))
            vote = "SUGGEST_APPROVE" if doc_complete and confidence >= 0.7 else (
                "SUGGEST_REJECT" if not doc_complete else "SUGGEST_MANUAL"
            )
            trace_credit_node(tool_client, state, "document_review", timer.elapsed_ms())
            return {
                "document_review_json": review_json,
                "document_review_result": review_result,
                "document_confidence": confidence,
                "verified_documents": doc_complete,
                "ai_summary": summary,
                "ticket_title": ticket_title,
                "ticket_description": ticket_desc,
                "degraded": False,
                "document_score": confidence,
                "agent_vote_document_review": vote,
                "cache_hit": True,
            }

    system = SystemMessage(content=prompt)
    ctx = state.get("unified_risk_context") or {}
    ctx_text = json.dumps(ctx, ensure_ascii=False) if ctx else ""
    human = HumanMessage(
        content=(
            f"purpose={state.get('purpose')}\n"
            f"applyAmount={state.get('apply_amount')}\n"
            f"applyTerm={state.get('apply_term')}\n"
            f"unifiedRiskContext={ctx_text}\n"
            f"productContext={json.dumps(ctx.get('productContext') or state.get('product_context') or {}, ensure_ascii=False)}\n"
            f"legacyContent={state.get('content')}\n"
            "请仅输出材料完整性/真实性风险分析，不要给出额度、利率、期限或产品推荐。"
        )
    )
    raw = invoke_llm(llm, [system, human], settings, tool_client=tool_client, audit=audit).content
    raw = raw if isinstance(raw, str) else str(raw)

    outcome = validate_or_repair_strict(
        raw, DocumentReviewSchema, llm, tool_client=tool_client, trace_id=tid
    )
    validated = outcome.model
    assert validated is not None
    doc_complete = validated.doc_complete
    summary = validated.summary
    ticket_title = validated.ticket_title
    ticket_desc = validated.ticket_description or state.get("content") or ""
    confidence = validated.confidence
    missing = validated.missing_docs
    review_result = validated.model_dump(by_alias=True)

    review_json = {
        "docComplete": doc_complete,
        "missingDocs": missing,
        "confidence": confidence,
        "summary": summary,
    }
    trace_node(tool_client, wf, tid, "DocumentReviewAgent", "end", decision=str(doc_complete))
    vote = "SUGGEST_APPROVE" if doc_complete and confidence >= 0.7 else (
        "SUGGEST_REJECT" if not doc_complete else "SUGGEST_MANUAL"
    )
    trace_credit_node(
        tool_client,
        state,
        "document_review",
        timer.elapsed_ms(),
        validation_outcome=outcome,
    )
    if settings.cache_enabled:
        set_ocr_cache(
            tool_client,
            file_md5,
            {
                "document_review_json": review_json,
                "document_review_result": review_result,
                "ticket_title": ticket_title,
                "ticket_description": ticket_desc,
            },
            trace_id=tid,
        )
    return {
        "document_review_json": review_json,
        "document_review_result": review_result,
        "document_confidence": confidence,
        "verified_documents": doc_complete,
        "ai_summary": summary,
        "ticket_title": ticket_title,
        "ticket_description": ticket_desc,
        "degraded": outcome.degraded,
        "document_score": confidence,
        "agent_vote_document_review": vote,
        "cache_hit": False,
    }
