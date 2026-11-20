from pathlib import Path

import httpx
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import create_app


def test_gdelt_429_falls_back_to_google_news_rss_and_filters_xss(tmp_path: Path) -> None:
    db_file = tmp_path / "fallback_test.db"

    def handler(request: httpx.Request) -> httpx.Response:
        if "gdeltproject" in str(request.url):
            return httpx.Response(429, text="Rate limited")
        rss_xml = """<?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0"><channel>
          <item>
            <title>افتتاح جسر جديد في الموصل لخدمة المواطنين</title>
            <link>https://ninawa.gov.iq/news/bridge</link>
            <pubDate>Thu, 19 Nov 2026 12:00:00 GMT</pubDate>
            <source url="https://ninawa.gov.iq">محافظة نينوى</source>
          </item>
          <item>
            <title>Malicious Link</title>
            <link>javascript:alert(1)</link>
          </item>
        </channel></rss>"""
        return httpx.Response(200, text=rss_xml)

    app = create_app(Settings(database_url=f"sqlite:///{db_file.as_posix()}"))
    app.state.http_transport = httpx.MockTransport(handler)

    with TestClient(app) as c:
        resp = c.get("/api/search", params={"q": "الموصل"})
        assert resp.status_code == 200
        body = resp.json()
        assert body["returned"] == 1
        assert body["provider"] == "Google News RSS"
        assert body["articles"][0]["url"] == "https://ninawa.gov.iq/news/bridge"
        assert body["articles"][0]["domain"] == "ninawa.gov.iq"


def test_upstream_failure_returns_200_from_local_sqlite_cache_instead_of_502(tmp_path: Path) -> None:
    db_file = tmp_path / "cache_test.db"

    def down_handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(503, text="Upstream unavailable")

    app = create_app(
        Settings(
            database_url=f"sqlite:///{db_file.as_posix()}",
            api_key="test-key",
        )
    )
    app.state.http_transport = httpx.MockTransport(down_handler)

    with TestClient(app) as c:
        # Seed local cache via CSV import
        csv_bytes = "title,url\nأخبار الموصل العاجلة اليوم,https://example.iq/mosul-1\n".encode("utf-8")
        imp = c.post(
            "/api/import/csv",
            headers={"X-API-Key": "test-key"},
            files={"file": ("seed.csv", csv_bytes, "text/csv")},
        )
        assert imp.status_code == 200

        # Both remote providers fail with 503 -> must return 200 OK from Local Archive Cache!
        resp = c.get("/api/search", params={"q": "الموصل"})
        assert resp.status_code == 200
        body = resp.json()
        assert body["provider"] == "Local Archive Cache"
        assert body["returned"] == 1
        assert body["articles"][0]["url"] == "https://example.iq/mosul-1"
