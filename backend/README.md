# تثبّت | Tathabbat – عين نينوى (Newsroom Backend for Fake Away)

Clean, hardened FastAPI + SQLAlchemy backend organized by **Vertical Slice Architecture**, serving both the trilingual (`ar` / `en` / `ckb`) Newsroom Web UI and the **Fake Away** Android mobile bridge (`/api/mobile/verify`, `/api/mobile/escalate`).

## Quick Start

```powershell
cd e:\hackathin\backend
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

- Web Newsroom UI: `http://127.0.0.1:8000`
- Swagger API Docs: `http://127.0.0.1:8000/docs`
- Default Reviewer API Key (`X-API-Key`): `tathabbat-dev-key` (override via `TATHABBAT_API_KEY` env var)
- SQLite database is auto-initialized at `backend/data/tathabbat.db` (override via `DATABASE_URL` env var)

## Tests

```powershell
cd e:\hackathin\backend
python -m pytest -v
```
