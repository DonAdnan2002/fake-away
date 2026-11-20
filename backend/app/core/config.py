import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    """Application configuration loaded from environment variables."""

    database_url: str = os.getenv("DATABASE_URL", "sqlite:///./data/tathabbat.db")
    api_key: str = os.getenv("TATHABBAT_API_KEY", "tathabbat-dev-key")
    gdelt_doc_url: str = os.getenv(
        "GDELT_DOC_URL", "https://api.gdeltproject.org/api/v2/doc/doc"
    )
    google_news_rss_url: str = os.getenv(
        "GOOGLE_NEWS_RSS_URL", "https://news.google.com/rss/search"
    )
    max_csv_bytes: int = int(os.getenv("MAX_CSV_BYTES", str(5 * 1024 * 1024)))
    cors_origins: str = os.getenv("CORS_ORIGINS", "*")
