from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.main import create_app


@pytest.fixture()
def test_settings(tmp_path: Path) -> Settings:
    db_file = tmp_path / "nested" / "data" / "tathabbat_test.db"
    return Settings(
        database_url=f"sqlite:///{db_file.as_posix()}",
        api_key="test-secret-key",
        max_csv_bytes=2048,
    )


@pytest.fixture()
def client(test_settings: Settings) -> TestClient:
    app = create_app(test_settings)
    with TestClient(app) as test_client:
        yield test_client
