from app.nodes.credit_ops.ocr_preprocess_node import _build_narrative, _build_structured, input_fusion_node


def test_input_fusion_merges_fields():
  state = {
    "user_id": 1,
    "product_id": 1,
    "apply_amount": 50000,
    "apply_term": 12,
    "purpose": "CONSUMER",
    "content": "希望装修",
    "income": 12000,
    "occupation": "工程师",
    "loan_purpose": "装修",
    "ocr_documents": [
      {"documentType": "BANK_STATEMENT", "text": "平均约8050元", "confidence": 0.96, "fileMd5": "x"}
    ],
  }

  class FakeTool:
    def invoke(self, tool, payload, trace_id=None):
      assert tool == "fuse_application_input"
      assert payload["structuredApplication"]["userId"] == 1
      assert payload["ocrDocuments"]
      return {
        "legacyContentSummary": "融合摘要",
        "crossCheckHints": ["收入不一致"],
      }

  delta = input_fusion_node(state, FakeTool())
  assert delta["unified_risk_context"]["crossCheckHints"]
  assert delta["content"] == "融合摘要"


def test_build_structured_from_state():
  structured = _build_structured({"user_id": 2, "apply_amount": 1000})
  assert structured["userId"] == 2
  assert structured["applyAmount"] == 1000
