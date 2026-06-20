from app.workflow.constants import NODE_ORDER


def test_workflow_does_not_include_credit_advisory():
    assert "credit_advisory" not in NODE_ORDER
    assert NODE_ORDER.index("anti_fraud") < NODE_ORDER.index("consensus")


def test_response_mapper_has_no_suggest_fields():
    from app.workflow.response_mapper import state_to_response_dict

    payload = state_to_response_dict({"workflow_id": "wf-1", "agent_suggestion": "SUGGEST_APPROVE"})
    assert "suggestAmount" not in payload
    assert "suggestRate" not in payload
    assert "suggestTerm" not in payload
    assert "keyRiskFactors" in payload


def test_consensus_outputs_risk_only_fields():
    from app.nodes.credit_ops.consensus_arbitrator import build_consensus

    result = build_consensus(
        {
            "verified_documents": True,
            "document_confidence": 0.9,
            "credit_eligible": True,
            "credit_assessment_confidence": 0.85,
            "fraud_score": 10,
            "unified_risk_context": {"productContext": {"maxAmount": 200000}},
            "apply_amount": 50000,
        }
    )
    assert result["consensus_suggestion"] in {"SUGGEST_APPROVE", "SUGGEST_REJECT", "SUGGEST_MANUAL_REVIEW"}
    assert "risk_level" in result
    assert "risk_summary" in result
    assert "suggestAmount" not in result
    assert "suggestRate" not in result
    assert "suggestTerm" not in result
