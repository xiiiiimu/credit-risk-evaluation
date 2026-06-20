from app.nodes.credit_ops.ocr_preprocess_node import ocr_preprocess_node
from app.workflow.retry import WorkflowManualReviewRequired
import pytest


def test_ocr_preprocess_skips_without_documents():
  delta = ocr_preprocess_node({}, _Fake([]))
  assert delta["ocr_preprocess_skipped"] is True


def test_ocr_low_confidence_manual_review():
  state = {"workflow_id": "wf", "uploaded_documents": [{"documentType": "BANK_STATEMENT", "fileMd5": "m1"}]}

  class Fake:
    def invoke(self, tool, payload, trace_id=None):
      return {"confidence": 0.5, "qualityFlags": [], "manualReviewRequired": False, "text": "x"}

  with pytest.raises(WorkflowManualReviewRequired):
    ocr_preprocess_node(state, Fake())


class _Fake:
  def __init__(self, docs):
    self.docs = docs

  def invoke(self, tool, payload, trace_id=None):
    return {"confidence": 0.96, "qualityFlags": [], "manualReviewRequired": False, "text": "ok", "cacheHit": True}
