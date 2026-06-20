from functools import partial

from langgraph.graph import END, START, StateGraph

from app.clients.mcp_credit_client import McpCreditClient
from app.clients.spring_tool_client import SpringToolClient
from app.config import Settings
from app.nodes.credit_ops.anti_fraud_agent import anti_fraud_agent
from app.nodes.credit_ops.consensus_node import consensus_node
from app.nodes.credit_ops.credit_assessment_agent import credit_assessment_agent
from app.nodes.credit_ops.document_review_agent import document_review_agent
from app.nodes.credit_ops.document_verify_node import document_verify_tool_node
from app.nodes.credit_ops.final_response_node import final_response_node
from app.nodes.credit_ops.memory_load_node import load_memory
from app.nodes.credit_ops.suggestion_routing_node import suggestion_routing_node
from app.state.credit_ops_state import CreditOpsState


def build_credit_risk_ops_graph(settings: Settings) -> StateGraph:
    tool_client = SpringToolClient(settings)
    mcp_client = McpCreditClient(settings)
    graph = StateGraph(CreditOpsState)

    graph.add_node("load_memory", partial(load_memory, tool_client=tool_client))
    graph.add_node(
        "document_review",
        partial(document_review_agent, settings=settings, tool_client=tool_client),
    )
    graph.add_node("document_verify", partial(document_verify_tool_node, tool_client=tool_client))
    graph.add_node(
        "credit_assessment",
        partial(credit_assessment_agent, settings=settings, tool_client=tool_client, mcp_client=mcp_client),
    )
    graph.add_node("anti_fraud", partial(anti_fraud_agent, settings=settings, tool_client=tool_client))
    graph.add_node("consensus", partial(consensus_node, tool_client=tool_client))
    graph.add_node("suggestion_routing", partial(suggestion_routing_node, tool_client=tool_client))
    graph.add_node("final", partial(final_response_node, tool_client=tool_client))

    graph.add_edge(START, "load_memory")
    graph.add_edge("load_memory", "document_review")
    graph.add_edge("document_review", "document_verify")
    graph.add_edge("document_verify", "credit_assessment")
    graph.add_edge("credit_assessment", "anti_fraud")
    graph.add_edge("anti_fraud", "consensus")
    graph.add_edge("consensus", "suggestion_routing")
    graph.add_edge("suggestion_routing", "final")
    graph.add_edge("final", END)

    return graph


def compile_credit_risk_ops_graph(settings: Settings):
    return build_credit_risk_ops_graph(settings).compile()
