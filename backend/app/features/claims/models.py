from datetime import datetime, timezone

from sqlalchemy import DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class Claim(Base):
    """Submitted or mobile-escalated claim awaiting or holding human verification."""

    __tablename__ = "claims"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    text: Mapped[str] = mapped_column(Text)
    language: Mapped[str] = mapped_column(String(8), default="ar")
    status: Mapped[str] = mapped_column(String(32), default="unverified", index=True)
    publisher: Mapped[str | None] = mapped_column(String(255), nullable=True)
    event_date: Mapped[str | None] = mapped_column(String(80), nullable=True)
    source_app: Mapped[str] = mapped_column(String(40), default="web")
    mobile_risk_score: Mapped[float | None] = mapped_column(Float, nullable=True)
    scan_type: Mapped[str | None] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utc_now)
    reviewed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    reviewer: Mapped[str | None] = mapped_column(String(255), nullable=True)
    rationale: Mapped[str | None] = mapped_column(Text, nullable=True)

    evidence: Mapped[list["Evidence"]] = relationship(
        back_populates="claim",
        cascade="all, delete-orphan",
    )
    audits: Mapped[list["Audit"]] = relationship(
        back_populates="claim",
        cascade="all, delete-orphan",
        order_by="Audit.created_at",
    )


class Evidence(Base):
    """Source URL and capture note attached to a claim."""

    __tablename__ = "evidence"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    claim_id: Mapped[int] = mapped_column(ForeignKey("claims.id"), index=True)
    label: Mapped[str] = mapped_column(String(255))
    url: Mapped[str] = mapped_column(Text)
    publisher: Mapped[str | None] = mapped_column(String(255), nullable=True)
    kind: Mapped[str] = mapped_column(String(40), default="source")
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    captured_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utc_now)

    claim: Mapped[Claim] = relationship(back_populates="evidence")


class Audit(Base):
    """Immutable audit log entry tracking lifecycle actions on a claim."""

    __tablename__ = "audit"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    claim_id: Mapped[int] = mapped_column(ForeignKey("claims.id"), index=True)
    action: Mapped[str] = mapped_column(String(80))
    details: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utc_now)

    claim: Mapped[Claim] = relationship(back_populates="audits")
