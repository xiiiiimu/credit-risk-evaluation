from unittest.mock import MagicMock

import pytest


class MockChatModel:
    """按顺序返回 invoke 结果的 mock LLM。"""

    def __init__(self, responses: list[str]):
        self._responses = list(responses)
        self._index = 0
        self.invoke_calls = 0

    def invoke(self, messages):
        self.invoke_calls += 1
        if self._index >= len(self._responses):
            content = self._responses[-1]
        else:
            content = self._responses[self._index]
            self._index += 1
        result = MagicMock()
        result.content = content
        return result
