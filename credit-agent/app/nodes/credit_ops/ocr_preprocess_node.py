from __future__ import annotations

from typing import Any

from app.clients.spring_tool_client import SpringToolClient
from app.workflow.retry import WorkflowManualReviewRequired


def ocr_preprocess_node(state: dict[str, Any], tool_client: SpringToolClient) -> dict[str, Any]:
  wf = state.get("workflow_id")
  tid = state.get("trace_id")
  documents = state.get("uploaded_documents") or []
  if not documents:
    return {"ocr_documents": [], "ocr_preprocess_skipped": True}

  ocr_documents: list[dict[str, Any]] = []
  for doc in documents:
    if not isinstance(doc, dict):
      continue
    payload = {
      "workflowId": wf,
      "traceId": tid,
      "nodeName": "ocr_preprocess",
      "documentType": doc.get("documentType") or doc.get("document_type") or "OTHER",
      "fileMd5": doc.get("fileMd5") or doc.get("file_md5"),
      "fileName": doc.get("fileName") or doc.get("file_name"),
      "mockText": doc.get("mockText") or doc.get("mock_text"),
    }
    result = tool_client.invoke("recognize_document", payload, trace_id=tid)
    if not isinstance(result, dict):
      continue
    ocr_documents.append(result)
    if result.get("manualReviewRequired"):
      flags = result.get("qualityFlags") or []
      raise WorkflowManualReviewRequired(
        "ocr_preprocess",
        "OCR_QUALITY_ISSUE",
        f"OCR 质量不满足要求: flags={flags}, confidence={result.get('confidence')}",
        0,
      )
    confidence = float(result.get("confidence") or 0)
    if confidence < 0.75:
      raise WorkflowManualReviewRequired(
        "ocr_preprocess",
        "OCR_LOW_CONFIDENCE",
        f"OCR 置信度过低: {confidence}",
        0,
      )

  return {"ocr_documents": ocr_documents, "ocr_preprocess_skipped": False}


def input_fusion_node(state: dict[str, Any], tool_client: SpringToolClient) -> dict[str, Any]:
  tid = state.get("trace_id")
  product_id = state.get("product_id")
  product_ctx = tool_client.invoke("get_credit_product", {"productId": product_id}, trace_id=tid) or {}
  tool_client.invoke("get_product_rule_config", {"productId": product_id}, trace_id=tid)
  tool_client.invoke("get_product_material_requirements", {"productId": product_id}, trace_id=tid)

  structured = _build_structured(state)
  narrative = _build_narrative(state)
  ocr_documents = state.get("ocr_documents") or []

  fused = tool_client.invoke(
    "fuse_application_input",
    {
      "structuredApplication": structured,
      "userNarrative": narrative,
      "ocrDocuments": ocr_documents,
      "legacyContent": state.get("content"),
      "productContext": product_ctx,
    },
    trace_id=tid,
  )
  if not isinstance(fused, dict):
    fused = {}

  legacy_summary = fused.get("legacyContentSummary") or state.get("content") or ""
  return {
    "unified_risk_context": fused,
    "structured_application": structured,
    "user_narrative": narrative,
    "product_context": product_ctx,
    "content": legacy_summary,
    "cross_check_hints": fused.get("crossCheckHints") or [],
  }


def _build_structured(state: dict[str, Any]) -> dict[str, Any]:
  if isinstance(state.get("structured_application"), dict):
    return state["structured_application"]
  return {
    "userId": state.get("user_id"),
    "productId": state.get("product_id"),
    "applyAmount": state.get("apply_amount"),
    "loanTerm": state.get("apply_term"),
    "income": state.get("income"),
    "occupation": state.get("occupation"),
    "age": state.get("age"),
    "contactInfo": state.get("contact_info"),
    "purpose": state.get("purpose"),
  }


def _build_narrative(state: dict[str, Any]) -> dict[str, Any]:
  if isinstance(state.get("user_narrative"), dict):
    narrative = dict(state["user_narrative"])
    if state.get("content") and not narrative.get("legacyContent"):
      narrative["legacyContent"] = state.get("content")
    return narrative
  return {
    "loanPurpose": state.get("loan_purpose"),
    "incomeDescription": state.get("income_description"),
    "occupationDescription": state.get("occupation_description"),
    "additionalDescription": state.get("additional_description"),
    "riskExplanation": state.get("risk_explanation"),
    "legacyContent": state.get("content"),
  }
