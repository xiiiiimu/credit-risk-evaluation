from functools import lru_cache
from pathlib import Path

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_ENV_FILE = _PROJECT_ROOT / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    openai_api_key: str = ""
    openai_base_url: str = "https://api.deepseek.com"
    openai_chat_model: str = "deepseek-chat"
    openai_embedding_model: str = "text-embedding-3-small"

    spring_tool_base_url: str = "http://127.0.0.1:8082"
    internal_api_key: str = "credit-agent-secret"

    agent_host: str = "0.0.0.0"
    agent_port: int = 8090

    tool_retry_max_attempts: int = 3
    tool_retry_backoff_ms: int = 300
    tool_circuit_failure_threshold: int = 5
    tool_circuit_open_sec: int = 30
    mcp_transport: str = "stdio"
    mcp_timeout_sec: float = 3.0
    mcp_server_command: str | None = None
    mcp_server_args: list[str] | None = None
    mcp_server_cwd: str | None = None

    agent_node_timeout_sec: float = 15.0
    agent_circuit_failure_threshold: int = 5
    agent_circuit_open_sec: int = 30
    llm_rate_limit_per_minute: int = 60
    llm_rate_limit_max_concurrent: int = 5
    llm_rate_limit_max_wait_sec: float = 30.0

    cache_enabled: bool = True
    llm_cache_ttl_hours: int = 24
    ocr_cache_ttl_hours: int = 72

    @field_validator("internal_api_key", "spring_tool_base_url", mode="before")
    @classmethod
    def strip_spaces(cls, v):
        if isinstance(v, str):
            return v.strip()
        return v


@lru_cache
def get_settings() -> Settings:
    return Settings()
