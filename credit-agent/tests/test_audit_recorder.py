from app.audit.recorder import AuditContext, record_audit


class _FakeToolClient:
    def __init__(self) -> None:
        self.calls: list[tuple[str, dict]] = []

    def invoke(self, tool: str, args: dict, trace_id: str | None = None, skip_audit: bool = False) -> dict:
        self.calls.append((tool, args))
        return {"id": 1}


def test_record_audit_invokes_save_audit_log():
    client = _FakeToolClient()
    audit = AuditContext(
        workflow_id="wf-1",
        trace_id="trace-1",
        node_name="document_review",
        prompt_version=2,
    )
    record_audit(
        client,
        call_type="LLM",
        audit=audit,
        request="req",
        response="resp",
        token_count=50,
        cost_time_ms=100,
    )
    assert client.calls[0][0] == "save_audit_log"
    assert client.calls[0][1]["workflowId"] == "wf-1"
    assert client.calls[0][1]["tokenCount"] == 50


def test_record_audit_skips_without_workflow_id():
    client = _FakeToolClient()
    record_audit(
        client,
        call_type="LLM",
        audit=AuditContext(workflow_id=None, node_name="x"),
        request="req",
        response="resp",
    )
    assert client.calls == []
