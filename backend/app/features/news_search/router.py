from fastapi import APIRouter, Depends, File, Query, Request, UploadFile
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.security import require_api_key
from app.features.news_search.schemas import ArticleOut, CsvImportResponse, SearchResponse
from app.features.news_search.service import import_csv_bytes, list_articles, search_news_providers

router = APIRouter(prefix="/api", tags=["news_search"])


@router.get("/search", response_model=SearchResponse)
async def search_news(
    request: Request,
    q: str = Query(min_length=2, max_length=300),
    limit: int = Query(default=40, ge=1, le=100),
    db: Session = Depends(get_db),
) -> SearchResponse:
    transport = getattr(request.app.state, "http_transport", None)
    return await search_news_providers(
        query=q,
        limit=limit,
        db=db,
        settings=request.app.state.settings,
        transport=transport,
    )


@router.get("/articles")
def get_articles(
    q: str = "",
    db: Session = Depends(get_db),
) -> dict[str, list[ArticleOut]]:
    return {"articles": list_articles(q, db)}


@router.post("/import/csv", response_model=CsvImportResponse, dependencies=[Depends(require_api_key)])
async def import_csv(
    request: Request,
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
) -> CsvImportResponse:
    raw = await file.read()
    return import_csv_bytes(
        filename=file.filename or "",
        raw_bytes=raw,
        db=db,
        max_bytes=request.app.state.settings.max_csv_bytes,
    )
