from datetime import datetime, timezone

from fastapi import HTTPException
from sqlalchemy import desc
from sqlalchemy.orm import Session, joinedload

from app.features.claims.models import Audit, Claim, Evidence
from app.features.claims.schemas import (
    AuditOut,
    ClaimIn,
    ClaimOut,
    EvidenceIn,
    EvidenceOut,
    ReviewIn,
    StatsOut,
)
from app.features.news_search.models import Article
from app.features.news_search.service import article_to_out


def evidence_to_out(item: Evidence) -> EvidenceOut:
    return EvidenceOut(
        id=item.id,
        label=item.label,
        url=item.url,
        publisher=item.publisher,
        kind=item.kind,
        note=item.note,
        captured_at=item.captured_at,
    )


def claim_to_out(claim: Claim) -> ClaimOut:
    return ClaimOut(
        id=claim.id,
        text=claim.text,
        language=claim.language,
        status=claim.status,
        publisher=claim.publisher,
        event_date=claim.event_date,
        source_app=claim.source_app,
        mobile_risk_score=claim.mobile_risk_score,
        scan_type=claim.scan_type,
        created_at=claim.created_at,
        reviewed_at=claim.reviewed_at,
        reviewer=claim.reviewer,
        rationale=claim.rationale,
        evidence=[evidence_to_out(e) for e in claim.evidence],
    )


def create_claim(payload: ClaimIn, db: Session) -> ClaimOut:
    claim = Claim(
        text=payload.text.strip(),
        language=payload.language,
        publisher=payload.publisher,
        event_date=payload.event_date,
        source_app="web",
    )
    db.add(claim)
    db.flush()
    db.add(Audit(claim_id=claim.id, action="created", details="Submitted via web dashboard"))
    db.commit()
    db.refresh(claim)
    return claim_to_out(claim)


def list_claims(db: Session) -> list[ClaimOut]:
    rows = (
        db.query(Claim)
        .options(joinedload(Claim.evidence))
        .order_by(desc(Claim.created_at))
        .all()
    )
    return [claim_to_out(row) for row in rows]


def add_evidence(claim_id: int, payload: EvidenceIn, db: Session) -> EvidenceOut:
    claim = db.query(Claim).filter_by(id=claim_id).first()
    if claim is None:
        raise HTTPException(status_code=404, detail="Claim not found")

    item = Evidence(
        claim_id=claim_id,
        label=payload.label.strip(),
        url=payload.url,
        publisher=payload.publisher,
        kind=payload.kind,
        note=payload.note,
    )
    db.add(item)
    db.add(Audit(claim_id=claim_id, action="evidence_added", details=item.label))
    db.commit()
    db.refresh(item)
    return evidence_to_out(item)


def review_claim(claim_id: int, payload: ReviewIn, db: Session) -> ClaimOut:
    claim = (
        db.query(Claim)
        .options(joinedload(Claim.evidence))
        .filter_by(id=claim_id)
        .first()
    )
    if claim is None:
        raise HTTPException(status_code=404, detail="Claim not found")

    claim.status = payload.status
    claim.reviewer = payload.reviewer.strip()
    claim.rationale = payload.rationale.strip()
    claim.reviewed_at = datetime.now(timezone.utc)
    db.add(Audit(claim_id=claim_id, action="reviewed", details=payload.model_dump_json()))
    db.commit()
    db.refresh(claim)
    return claim_to_out(claim)


def list_claim_audits(claim_id: int, db: Session) -> list[AuditOut]:
    claim = db.query(Claim).filter_by(id=claim_id).first()
    if claim is None:
        raise HTTPException(status_code=404, detail="Claim not found")
    rows = (
        db.query(Audit)
        .filter_by(claim_id=claim_id)
        .order_by(Audit.created_at.asc(), Audit.id.asc())
        .all()
    )
    return [
        AuditOut(
            id=row.id,
            claim_id=row.claim_id,
            action=row.action,
            details=row.details,
            created_at=row.created_at,
        )
        for row in rows
    ]


def get_stats(db: Session) -> StatsOut:
    return StatsOut(
        articles=db.query(Article).count(),
        claims=db.query(Claim).count(),
        evidence=db.query(Evidence).count(),
        in_review=db.query(Claim).filter_by(status="in_review").count(),
        mobile_escalations=db.query(Claim).filter_by(source_app="fake_away_android").count(),
    )


def export_all(db: Session) -> dict:
    articles = [article_to_out(a).model_dump(mode="json") for a in db.query(Article).all()]
    claims = [
        claim_to_out(c).model_dump(mode="json")
        for c in db.query(Claim).options(joinedload(Claim.evidence)).all()
    ]
    return {"articles": articles, "claims": claims}
