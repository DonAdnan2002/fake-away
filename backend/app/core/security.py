import hmac
from urllib.parse import urlparse

from fastapi import Header, HTTPException, Request

SAFE_URL_SCHEMES = {"http", "https"}


def is_safe_http_url(url: str) -> bool:
    """Allow only absolute http/https URLs with a valid host to block XSS schemes."""
    if not url or not isinstance(url, str):
        return False
    parsed = urlparse(url.strip())
    return parsed.scheme.lower() in SAFE_URL_SCHEMES and bool(parsed.netloc)


def require_api_key(
    request: Request,
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
) -> str:
    """Validate reviewer/admin API key using constant-time comparison."""
    expected: str = request.app.state.settings.api_key
    if not x_api_key or not hmac.compare_digest(x_api_key.strip(), expected):
        raise HTTPException(
            status_code=401,
            detail="Missing or invalid X-API-Key header.",
        )
    return x_api_key
