from pydantic import BaseModel, Field

from app.features.claims.schemas import EvidenceOut
from app.features.news_search.schemas import ArticleOut


class MobileVerifyRequest(BaseModel):
    text: str = Field(min_length=3, max_length=12000)
    language: str = Field(default="ar", max_length=10)


class MatchedClaimOut(BaseModel):
    id: int
    text: str
    status: str
    reviewer: str | None = None
    rationale: str | None = None
    similarity: float
    evidence: list[EvidenceOut]


class MobileVerifyResponse(BaseModel):
    matched_claim: MatchedClaimOut | None = None
    related_articles: list[ArticleOut]
    note: str


class MobileEscalateRequest(BaseModel):
    text: str = Field(min_length=3, max_length=12000)
    language: str = Field(default="ar", max_length=10)
    offline_risk_score: float = Field(default=0.5, ge=0.0, le=1.0)
    scan_type: str = Field(default="NEWS_TEXT", max_length=32)
    summary: str | None = Field(default=None, max_length=2000)
    publisher: str | None = Field(default="Fake Away Android", max_length=255)


class MobileEscalateResponse(BaseModel):
    claim_id: int
    status: str
    message: str


class CompareMatchOut(BaseModel):
    type: str
    text: str
    status: str | None = None
    url: str | None = None
    score: float


class CompareResponse(BaseModel):
    matches: list[CompareMatchOut]
    note: str
