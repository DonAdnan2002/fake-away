from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.security import require_api_key
from app.features.claims.schemas import (
    AuditOut,
    ClaimIn,
    ClaimOut,
    EvidenceIn,
    EvidenceOut,
    ReviewIn,
    StatsOut,
)
from app.features.claims.service import (
    add_evidence,
    create_claim,
    export_all,
    get_stats,
    list_claim_audits,
    list_claims,
    review_claim,
)

router = APIRouter(prefix="/api", tags=["claims"])


@router.post("/claims", response_model=ClaimOut)
def post_claim(payload: ClaimIn, db: Session = Depends(get_db)) -> ClaimOut:
    return create_claim(payload, db)


@router.get("/claims")
def get_claims(db: Session = Depends(get_db)) -> dict[str, list[ClaimOut]]:
    return {"claims": list_claims(db)}


@router.post("/claims/{claim_id}/evidence", response_model=EvidenceOut)
def post_evidence(
    claim_id: int,
    payload: EvidenceIn,
    db: Session = Depends(get_db),
) -> EvidenceOut:
    return add_evidence(claim_id, payload, db)


@router.patch("/claims/{claim_id}/review", response_model=ClaimOut, dependencies=[Depends(require_api_key)])
def patch_review(
    claim_id: int,
    payload: ReviewIn,
    db: Session = Depends(get_db),
) -> ClaimOut:
    return review_claim(claim_id, payload, db)


@router.get("/claims/{claim_id}/audit")
def get_claim_audit(claim_id: int, db: Session = Depends(get_db)) -> dict[str, list[AuditOut]]:
    return {"audits": list_claim_audits(claim_id, db)}


@router.get("/stats", response_model=StatsOut)
def stats(db: Session = Depends(get_db)) -> StatsOut:
    return get_stats(db)


@router.get("/export", dependencies=[Depends(require_api_key)])
def export_data(db: Session = Depends(get_db)) -> dict:
    return export_all(db)
