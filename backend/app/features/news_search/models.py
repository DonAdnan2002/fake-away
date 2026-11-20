from datetime import datetime, timezone

from sqlalchemy import DateTime, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class Article(Base):
    """Metadata record for an article retrieved via GDELT DOC or imported via CSV."""

    __tablename__ = "articles"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    external_id: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    title: Mapped[str] = mapped_column(Text)
    url: Mapped[str] = mapped_column(Text)
    domain: Mapped[str | None] = mapped_column(String(255), nullable=True)
    language: Mapped[str | None] = mapped_column(String(80), nullable=True)
    source_country: Mapped[str | None] = mapped_column(String(80), nullable=True)
    published_at: Mapped[str | None] = mapped_column(String(80), nullable=True)
    image_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    provider: Mapped[str] = mapped_column(String(80), default="GDELT DOC")
    imported_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utc_now)
