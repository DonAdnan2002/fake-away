from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator

from app.core.security import is_safe_http_url

ClaimStatus = Literal["unverified", "in_review", "supported", "false", "misleading", "insufficient"]


class ClaimIn(BaseModel):
    text: str = Field(min_length=5, max_length=12000)
    language: str = Field(default="ar", max_length=10)
    publisher: str | None = Field(default=None, max_length=255)
    event_date: str | None = Field(default=None, max_length=80)


class EvidenceIn(BaseModel):
    label: str = Field(min_length=2, max_length=255)
    url: str = Field(min_length=8, max_length=2000)
    publisher: str | None = Field(default=None, max_length=255)
    kind: str = Field(default="source", max_length=40)
    note: str | None = Field(default=None, max_length=2000)

    @field_validator("url")
    @classmethod
    def validate_http_url(cls, value: str) -> str:
        cleaned = value.strip()
        if not is_safe_http_url(cleaned):
            raise ValueError("Evidence URL must use http:// or https://")
        return cleaned


class ReviewIn(BaseModel):
    status: ClaimStatus
    reviewer: str = Field(min_length=2, max_length=255)
    rationale: str = Field(min_length=5, max_length=4000)


class EvidenceOut(BaseModel):
    id: int
    label: str
    url: str
    publisher: str | None = None
    kind: str
    note: str | None = None
    captured_at: datetime


class AuditOut(BaseModel):
    id: int
    claim_id: int
    action: str
    details: str | None = None
    created_at: datetime


class ClaimOut(BaseModel):
    id: int
    text: str
    language: str
    status: str
    publisher: str | None = None
    event_date: str | None = None
    source_app: str
    mobile_risk_score: float | None = None
    scan_type: str | None = None
    created_at: datetime
    reviewed_at: datetime | None = None
    reviewer: str | None = None
    rationale: str | None = None
    evidence: list[EvidenceOut]


class StatsOut(BaseModel):
    articles: int
    claims: int
    evidence: int
    in_review: int
    mobile_escalations: int
