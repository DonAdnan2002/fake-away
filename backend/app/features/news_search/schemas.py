from pydantic import BaseModel


class ArticleOut(BaseModel):
    id: int
    title: str
    url: str
    domain: str | None = None
    language: str | None = None
    country: str | None = None
    published_at: str | None = None
    provider: str


class SearchResponse(BaseModel):
    provider: str
    returned: int
    articles: list[ArticleOut]
    coverage_note: str


class CsvImportResponse(BaseModel):
    imported: int
    skipped_duplicates: int
    skipped_invalid: int
    note: str
