from pathlib import Path

from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, Response
from fastapi.staticfiles import StaticFiles
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.core.database import Base, create_db_engine, create_session_factory, get_db
from app.features.claims import models as _claims_models  # noqa: F401
from app.features.claims.models import Claim
from app.features.claims.router import router as claims_router
from app.features.mobile_bridge.router import router as mobile_router
from app.features.news_search import models as _news_models  # noqa: F401
from app.features.news_search.models import Article
from app.features.news_search.router import router as news_router

BACKEND_ROOT = Path(__file__).resolve().parent.parent
STATIC_DIR = BACKEND_ROOT / "static"


def create_app(settings: Settings | None = None) -> FastAPI:
    """Build and configure the Tathabbat FastAPI application."""
    cfg = settings or Settings()
    engine = create_db_engine(cfg.database_url)
    Base.metadata.create_all(bind=engine)

    application = FastAPI(title="Tathabbat | عين نينوى – Fake Away Newsroom", version="2.0.0")
    application.state.settings = cfg
    application.state.engine = engine
    application.state.session_factory = create_session_factory(engine)

    origins = [o.strip() for o in cfg.cors_origins.split(",") if o.strip()] or ["*"]
    application.add_middleware(
        CORSMiddleware,
        allow_origins=origins,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    if STATIC_DIR.exists():
        application.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")

    @application.get("/")
    def home() -> FileResponse:
        return FileResponse(str(STATIC_DIR / "index.html"))

    @application.get("/sw.js")
    def unregister_stale_service_worker() -> Response:
        """Self-unregistering kill switch for any stale Service Worker cached on port 8000."""
        script = (
            "self.addEventListener('install', () => self.skipWaiting());\n"
            "self.addEventListener('activate', (event) => {\n"
            "  event.waitUntil(\n"
            "    self.registration.unregister().then(() => self.clients.matchAll())\n"
            "      .then((clients) => clients.forEach((c) => c.navigate(c.url)))\n"
            "  );\n"
            "});\n"
        )
        return Response(
            content=script,
            media_type="application/javascript",
            headers={"Cache-Control": "no-store, no-cache, must-revalidate"},
        )

    @application.get("/api/health")
    def health(db: Session = Depends(get_db)) -> dict[str, object]:
        return {
            "status": "ok",
            "service": "tathabbat_newsroom",
            "articles": db.query(Article).count(),
            "claims": db.query(Claim).count(),
        }

    application.include_router(news_router)
    application.include_router(claims_router)
    application.include_router(mobile_router)
    return application


app = create_app()
