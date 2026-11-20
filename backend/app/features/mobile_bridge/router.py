from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.features.mobile_bridge.schemas import (
    CompareResponse,
    MobileEscalateRequest,
    MobileEscalateResponse,
    MobileVerifyRequest,
    MobileVerifyResponse,
)
from app.features.mobile_bridge.service import (
    compare_query,
    escalate_mobile_claim,
    verify_mobile_claim,
)

router = APIRouter(prefix="/api", tags=["mobile_bridge"])


@router.post("/mobile/verify", response_model=MobileVerifyResponse)
def post_mobile_verify(
    payload: MobileVerifyRequest,
    db: Session = Depends(get_db),
) -> MobileVerifyResponse:
    return verify_mobile_claim(payload, db)


@router.post("/mobile/escalate", response_model=MobileEscalateResponse)
def post_mobile_escalate(
    payload: MobileEscalateRequest,
    db: Session = Depends(get_db),
) -> MobileEscalateResponse:
    return escalate_mobile_claim(payload, db)


@router.get("/compare", response_model=CompareResponse)
def get_compare(
    q: str = Query(min_length=3, max_length=500),
    db: Session = Depends(get_db),
) -> CompareResponse:
    return compare_query(q, db)
