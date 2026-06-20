from pydantic import BaseModel, ConfigDict, Field


class DocumentReviewSchema(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    doc_complete: bool = Field(alias="docComplete")
    missing_docs: list[str] = Field(default_factory=list, alias="missingDocs")
    confidence: float = 0.7
    summary: str = ""
    ticket_title: str = Field(default="信贷资料复核", alias="ticketTitle")
    ticket_description: str = Field(default="", alias="ticketDescription")
