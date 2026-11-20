import csv
import hashlib
import io
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from urllib.parse import urlparse

import httpx
from fastapi import HTTPException
from sqlalchemy import desc, or_
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.core.security import is_safe_http_url
from app.core.text_normalizer import compute_similarity
from app.features.news_search.models import Article
from app.features.news_search.schemas import ArticleOut, CsvImportResponse, SearchResponse


@dataclass(frozen=True)
class _CandidateArticle:
    title: str
    url: str
    domain: str | None
    language: str | None
    country: str | None
    published_at: str | None
    image_url: str | None
    provider: str


def article_to_out(article: Article) -> ArticleOut:
    return ArticleOut(
        id=article.id,
        title=article.title,
        url=article.url,
        domain=article.domain,
        language=article.language,
        country=article.source_country,
        published_at=article.published_at,
        provider=article.provider,
    )


def _contains_arabic_script(text: str) -> bool:
    return any("\u0600" <= ch <= "\u06FF" or "\u0750" <= ch <= "\u077F" for ch in text)


async def _fetch_gdelt_candidates(
    client: httpx.AsyncClient,
    query: str,
    limit: int,
    settings: Settings,
) -> tuple[list[_CandidateArticle], bool]:
    params = {
        "query": query,
        "mode": "ArtList",
        "format": "json",
        "maxrecords": limit,
        "sort": "DateDesc",
    }
    try:
        resp = await client.get(settings.gdelt_doc_url, params=params, timeout=7.0)
        resp.raise_for_status()
        data = resp.json()
    except Exception:
        return [], False

    candidates: list[_CandidateArticle] = []
    for raw in data.get("articles", []):
        url = (raw.get("url") or raw.get("url_mobile") or "").strip()
        title = (raw.get("title") or "").strip()
        if not title or not is_safe_http_url(url):
            continue
        social_img = (raw.get("socialimage") or "").strip()
        candidates.append(
            _CandidateArticle(
                title=title,
                url=url,
                domain=raw.get("domain") or urlparse(url).netloc,
                language=raw.get("language"),
                country=raw.get("sourcecountry"),
                published_at=raw.get("seendate"),
                image_url=social_img if is_safe_http_url(social_img) else None,
                provider="GDELT DOC",
            )
        )
    return candidates, True


async def _fetch_google_news_candidates(
    client: httpx.AsyncClient,
    query: str,
    limit: int,
    settings: Settings,
) -> tuple[list[_CandidateArticle], bool]:
    is_rtl = _contains_arabic_script(query)
    params = {
        "q": query,
        "hl": "ar" if is_rtl else "en-US",
        "gl": "IQ" if is_rtl else "US",
        "ceid": "IQ:ar" if is_rtl else "US:en",
    }
    try:
        resp = await client.get(settings.google_news_rss_url, params=params, timeout=10.0)
        resp.raise_for_status()
        root = ET.fromstring(resp.text)
    except Exception:
        return [], False

    candidates: list[_CandidateArticle] = []
    for item in root.findall(".//item")[:limit]:
        title = (item.findtext("title") or "").strip()
        link = (item.findtext("link") or "").strip()
        if not title or not is_safe_http_url(link):
            continue
        pub_date = (item.findtext("pubDate") or "").strip() or None
        source_el = item.find("source")
        source_name = (source_el.text or "").strip() if source_el is not None and source_el.text else ""
        source_url = source_el.attrib.get("url", "").strip() if source_el is not None else ""
        domain = urlparse(source_url).netloc if is_safe_http_url(source_url) else (source_name or urlparse(link).netloc)
        candidates.append(
            _CandidateArticle(
                title=title,
                url=link,
                domain=domain,
                language="Arabic/Kurdish" if is_rtl else "English",
                country="IQ" if is_rtl else "US",
                published_at=pub_date,
                image_url=None,
                provider="Google News RSS",
            )
        )
    return candidates, True


def _upsert_candidates(
    candidates: list[_CandidateArticle],
    db: Session,
) -> list[ArticleOut]:
    found: list[ArticleOut] = []
    seen_ids: set[str] = set()

    for cand in candidates:
        ext_id = hashlib.sha256(cand.url.encode("utf-8")).hexdigest()
        if ext_id in seen_ids:
            continue
        seen_ids.add(ext_id)

        existing = db.query(Article).filter_by(external_id=ext_id).first()
        if existing is None:
            existing = Article(
                external_id=ext_id,
                title=cand.title,
                url=cand.url,
                domain=cand.domain,
                language=cand.language,
                source_country=cand.country,
                published_at=cand.published_at,
                image_url=cand.image_url,
                provider=cand.provider,
            )
            db.add(existing)
            db.flush()

        found.append(article_to_out(existing))

    if found:
        db.commit()
    return found


def _search_local_cache(query: str, limit: int, db: Session) -> list[ArticleOut]:
    recent = db.query(Article).order_by(desc(Article.imported_at)).limit(800).all()
    q_lower = query.strip().lower()
    scored: list[tuple[float, Article]] = []
    for row in recent:
        sim = compute_similarity(query, row.title)
        if q_lower in row.title.lower() or sim >= 0.12:
            scored.append((max(sim, 0.5 if q_lower in row.title.lower() else sim), row))
    scored.sort(key=lambda pair: pair[0], reverse=True)
    return [article_to_out(row) for _, row in scored[:limit]]


async def search_news_providers(
    query: str,
    limit: int,
    db: Session,
    settings: Settings,
    transport: httpx.AsyncBaseTransport | None = None,
) -> SearchResponse:
    """Query GDELT DOC + Google News RSS with graceful local SQLite fallback (never throws 502)."""
    async with httpx.AsyncClient(
        follow_redirects=True,
        headers={"User-Agent": "Tathabbat-FakeAway/2.0"},
        transport=transport,
    ) as client:
        gdelt_items, gdelt_ok = await _fetch_gdelt_candidates(client, query, limit, settings)
        gnews_items, gnews_ok = await _fetch_google_news_candidates(client, query, limit, settings)

    combined = (gdelt_items + gnews_items)[:limit]
    persisted = _upsert_candidates(combined, db)

    providers_used: list[str] = []
    if gdelt_items:
        providers_used.append("GDELT DOC")
    if gnews_items:
        providers_used.append("Google News RSS")

    if persisted:
        provider_label = " + ".join(providers_used)
        note = "Live news metadata retrieved and archived locally."
        return SearchResponse(
            provider=provider_label,
            returned=len(persisted),
            articles=persisted,
            coverage_note=note,
        )

    cached = _search_local_cache(query, limit, db)
    if cached:
        return SearchResponse(
            provider="Local Archive Cache",
            returned=len(cached),
            articles=cached,
            coverage_note="Upstream news feeds returned empty or unavailable; showing matching archived records.",
        )

    status_note = (
        "No recent matching articles found in GDELT or Google News RSS."
        if (gdelt_ok or gnews_ok)
        else "Upstream news providers temporarily unavailable; 0 matching local records."
    )
    return SearchResponse(
        provider="GDELT DOC + Google News RSS",
        returned=0,
        articles=[],
        coverage_note=status_note,
    )


search_gdelt = search_news_providers


def list_articles(query: str, db: Session) -> list[ArticleOut]:
    stmt = db.query(Article)
    if query.strip():
        pattern = f"%{query.strip()}%"
        stmt = stmt.filter(or_(Article.title.ilike(pattern), Article.domain.ilike(pattern)))
    rows = stmt.order_by(desc(Article.imported_at)).limit(500).all()
    return [article_to_out(row) for row in rows]


def import_csv_bytes(
    filename: str,
    raw_bytes: bytes,
    db: Session,
    max_bytes: int,
) -> CsvImportResponse:
    """Safely import UTF-8 CSV archive rows while deduplicating intra-file URLs and blocking unsafe schemes."""
    if not filename.lower().endswith(".csv"):
        raise HTTPException(status_code=400, detail="Only .csv files are accepted.")
    if len(raw_bytes) > max_bytes:
        raise HTTPException(status_code=413, detail=f"CSV exceeds max allowed size ({max_bytes} bytes).")

    try:
        decoded = raw_bytes.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise HTTPException(status_code=400, detail="CSV must be UTF-8 encoded.") from exc

    reader = csv.DictReader(io.StringIO(decoded))
    if not reader.fieldnames or not {"title", "url"}.issubset(reader.fieldnames):
        raise HTTPException(status_code=400, detail="Required CSV columns: title,url")

    imported = 0
    skipped_duplicates = 0
    skipped_invalid = 0
    seen_ids: set[str] = set()

    for row in reader:
        title = (row.get("title") or "").strip()
        url = (row.get("url") or "").strip()
        if not title or not is_safe_http_url(url):
            skipped_invalid += 1
            continue

        ext_id = hashlib.sha256(url.encode("utf-8")).hexdigest()
        if ext_id in seen_ids or db.query(Article).filter_by(external_id=ext_id).first() is not None:
            skipped_duplicates += 1
            continue

        seen_ids.add(ext_id)
        db.add(
            Article(
                external_id=ext_id,
                title=title,
                url=url,
                domain=(row.get("domain") or "").strip() or urlparse(url).netloc,
                language=(row.get("language") or "").strip() or None,
                source_country=(row.get("source_country") or "").strip() or None,
                published_at=(row.get("published_at") or "").strip() or None,
                provider="Historical CSV import",
            )
        )
        imported += 1

    db.commit()
    return CsvImportResponse(
        imported=imported,
        skipped_duplicates=skipped_duplicates,
        skipped_invalid=skipped_invalid,
        note="Check provenance, licensing, and source metadata before publishing.",
    )
