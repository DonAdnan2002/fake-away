from sqlalchemy import desc
from sqlalchemy.orm import Session, joinedload

from app.core.text_normalizer import compute_similarity
from app.features.claims.models import Audit, Claim
from app.features.claims.service import evidence_to_out
from app.features.mobile_bridge.schemas import (
    CompareMatchOut,
    CompareResponse,
    MatchedClaimOut,
    MobileEscalateRequest,
    MobileEscalateResponse,
    MobileVerifyRequest,
    MobileVerifyResponse,
)
from app.features.news_search.models import Article
from app.features.news_search.service import article_to_out

REVIEWED_STATUSES = {"supported", "false", "misleading"}


def verify_mobile_claim(req: MobileVerifyRequest, db: Session) -> MobileVerifyResponse:
    """Match a mobile claim against human-reviewed Tathabbat claims and recent news archive."""
    claims = db.query(Claim).options(joinedload(Claim.evidence)).all()

    best_reviewed: tuple[Claim, float] | None = None
    best_any: tuple[Claim, float] | None = None

    for claim in claims:
        score = compute_similarity(req.text, claim.text)
        if score < 0.30:
            continue
        if claim.status in REVIEWED_STATUSES:
            if best_reviewed is None or score > best_reviewed[1]:
                best_reviewed = (claim, score)
        if best_any is None or score > best_any[1]:
            best_any = (claim, score)

    chosen = best_reviewed or best_any
    matched_out = None
    if chosen is not None:
        c, sim_score = chosen
        matched_out = MatchedClaimOut(
            id=c.id,
            text=c.text,
            status=c.status,
            reviewer=c.reviewer,
            rationale=c.rationale,
            similarity=sim_score,
            evidence=[evidence_to_out(e) for e in c.evidence],
        )

    recent_articles = (
        db.query(Article)
        .order_by(desc(Article.imported_at))
        .limit(1000)
        .all()
    )
    scored_articles = []
    for art in recent_articles:
        score = compute_similarity(req.text, art.title)
        if score >= 0.20:
            scored_articles.append((score, art))
    scored_articles.sort(key=lambda pair: pair[0], reverse=True)

    return MobileVerifyResponse(
        matched_claim=matched_out,
        related_articles=[article_to_out(art) for _, art in scored_articles[:5]],
        note="Matches verified via Arabic/Kurdish/English normalized similarity.",
    )


def escalate_mobile_claim(req: MobileEscalateRequest, db: Session) -> MobileEscalateResponse:
    """Enqueue a suspicious mobile scan into the Tathabbat human verification queue."""
    claim = Claim(
        text=req.text.strip(),
        language=req.language,
        status="in_review",
        publisher=req.publisher or "Fake Away Android",
        source_app="fake_away_android",
        mobile_risk_score=req.offline_risk_score,
        scan_type=req.scan_type,
    )
    db.add(claim)
    db.flush()

    detail = (
        f"Escalated from Fake Away ({req.scan_type}, offline_risk={round(req.offline_risk_score * 100)}%)"
    )
    if req.summary:
        detail = f"{detail} | {req.summary[:240]}"
    db.add(Audit(claim_id=claim.id, action="mobile_escalated", details=detail))
    db.commit()
    db.refresh(claim)

    return MobileEscalateResponse(
        claim_id=claim.id,
        status=claim.status,
        message="Claim queued in Tathabbat newsroom for human verification.",
    )


def compare_query(query: str, db: Session) -> CompareResponse:
    """Normalized lexical comparison across stored claims and latest 1000 articles."""
    matches: list[CompareMatchOut] = []
    for claim in db.query(Claim).all():
        score = compute_similarity(query, claim.text)
        if score >= 0.12:
            matches.append(
                CompareMatchOut(
                    type="claim",
                    text=claim.text,
                    status=claim.status,
                    score=score,
                )
            )

    articles = db.query(Article).order_by(desc(Article.imported_at)).limit(1000).all()
    for art in articles:
        score = compute_similarity(query, art.title)
        if score >= 0.12:
            matches.append(
                CompareMatchOut(
                    type="article",
                    text=art.title,
                    url=art.url,
                    score=score,
                )
            )

    matches.sort(key=lambda item: item.score, reverse=True)
    return CompareResponse(
        matches=matches[:50],
        note="Normalized similarity across Arabic, Kurdish Sorani, and English is discovery assistance.",
    )
