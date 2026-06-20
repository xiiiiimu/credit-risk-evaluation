"""启动征信 MCP Server：python -m app.mcp_server"""

from app.mcp_server.server import mcp

if __name__ == "__main__":
    mcp.run(transport="stdio")
