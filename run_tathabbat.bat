@echo off
cd /d "%~dp0backend"
echo Starting Tathabbat Newsroom Backend on http://127.0.0.1:8000 (and http://localhost:8000) ...
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
pause
