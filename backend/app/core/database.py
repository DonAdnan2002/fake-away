from pathlib import Path
from typing import Generator

from fastapi import Request
from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker


class Base(DeclarativeBase):
    """Shared SQLAlchemy declarative base across vertical slices."""


def _ensure_sqlite_parent_dir(database_url: str) -> None:
    if not database_url.startswith("sqlite:///"):
        return
    raw_path = database_url.removeprefix("sqlite:///")
    if not raw_path or raw_path == ":memory:" or raw_path.startswith("file::memory:"):
        return
    Path(raw_path).parent.mkdir(parents=True, exist_ok=True)


def create_db_engine(database_url: str) -> Engine:
    """Create a SQLAlchemy engine, ensuring local SQLite directories exist first."""
    _ensure_sqlite_parent_dir(database_url)
    connect_args = {"check_same_thread": False} if database_url.startswith("sqlite") else {}
    return create_engine(database_url, connect_args=connect_args, pool_pre_ping=True)


def create_session_factory(engine: Engine) -> sessionmaker[Session]:
    """Build a session factory bound to [engine]."""
    return sessionmaker(bind=engine, autoflush=False, autocommit=False)


def get_db(request: Request) -> Generator[Session, None, None]:
    """FastAPI dependency yielding a scoped DB session from app.state."""
    factory: sessionmaker[Session] = request.app.state.session_factory
    db = factory()
    try:
        yield db
    finally:
        db.close()
