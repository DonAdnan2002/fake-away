from pathlib import Path

from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import create_app


def test_auto_creates_missing_nested_sqlite_directory(tmp_path: Path) -> None:
    nested_db = tmp_path / "deep" / "dir" / "tathabbat.db"
    assert not nested_db.parent.exists()
    app = create_app(Settings(database_url=f"sqlite:///{nested_db.as_posix()}"))
    with TestClient(app) as c:
        resp = c.get("/api/health")
        assert resp.status_code == 200
        assert resp.json()["status"] == "ok"
    assert nested_db.exists()


def test_csv_import_handles_intrafile_duplicates_and_rejects_unsafe_urls(client: TestClient) -> None:
    csv_body = (
        "title,url,domain\n"
        "Official Ministry Statement,https://gov.iq/news/1,gov.iq\n"
        "Duplicate Same URL Inside CSV,https://gov.iq/news/1,gov.iq\n"
        "XSS Attack Link,javascript:alert(1),evil\n"
        "Valid Kurdish Report,https://rudaw.net/sorani/news/2,rudaw.net\n"
    ).encode("utf-8")

    # Missing API key must return 401
    unauth = client.post(
        "/api/import/csv",
        files={"file": ("archive.csv", csv_body, "text/csv")},
    )
    assert unauth.status_code == 401

    resp = client.post(
        "/api/import/csv",
        headers={"X-API-Key": "test-secret-key"},
        files={"file": ("archive.csv", csv_body, "text/csv")},
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["imported"] == 2
    assert data["skipped_duplicates"] == 1
    assert data["skipped_invalid"] == 1


def test_csv_import_rejects_oversized_upload(client: TestClient) -> None:
    huge = ("title,url\n" + ("Row,https://example.com/x\n" * 200)).encode("utf-8")
    resp = client.post(
        "/api/import/csv",
        headers={"X-API-Key": "test-secret-key"},
        files={"file": ("big.csv", huge, "text/csv")},
    )
    assert resp.status_code == 413


def test_evidence_blocks_javascript_xss_and_review_requires_api_key(client: TestClient) -> None:
    created = client.post("/api/claims", json={"text": "ادعاء تجريبي لفحص الأمان", "language": "ar"}).json()
    cid = created["id"]

    xss_resp = client.post(
        f"/api/claims/{cid}/evidence",
        json={"label": "XSS", "url": "javascript:alert(document.cookie)"},
    )
    assert xss_resp.status_code == 422

    unauth_review = client.patch(
        f"/api/claims/{cid}/review",
        json={"status": "false", "reviewer": "Ali", "rationale": "Confirmed rumor"},
    )
    assert unauth_review.status_code == 401

    unauth_export = client.get("/api/export")
    assert unauth_export.status_code == 401
