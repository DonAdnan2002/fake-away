import re

_DIACRITICS_RE = re.compile(r"[\u064B-\u065F\u0670\u0640]")
_TOKEN_RE = re.compile(r"[a-z0-9\u0600-\u06FF\u0750-\u077F]{2,}")

_CHAR_MAP = str.maketrans(
    {
        "أ": "ا",
        "إ": "ا",
        "آ": "ا",
        "ٱ": "ا",
        "ة": "ه",
        "ى": "ي",
        "ی": "ي",
        "ک": "ك",
        "ە": "ه",
        "ۆ": "و",
        "ێ": "ي",
        "ڕ": "ر",
        "ڵ": "ل",
    }
)


def normalize_text(text: str) -> str:
    """Normalize Arabic, Kurdish Sorani, and English characters for reliable matching."""
    cleaned = _DIACRITICS_RE.sub("", text.lower())
    return cleaned.translate(_CHAR_MAP)


def extract_normalized_tokens(text: str) -> set[str]:
    """Extract normalized tokens, stripping leading Arabic definite article 'ال' on >=5-char words."""
    normalized = normalize_text(text)
    tokens: set[str] = set()
    for tok in _TOKEN_RE.findall(normalized):
        if len(tok) >= 5 and tok.startswith("ال"):
            tokens.add(tok[2:])
        else:
            tokens.add(tok)
    return tokens


def _char_trigrams(text: str) -> set[str]:
    compact = re.sub(r"\s+", " ", normalize_text(text)).strip()
    if len(compact) < 3:
        return {compact} if compact else set()
    return {compact[i : i + 3] for i in range(len(compact) - 2)}


def _jaccard(a: set[str], b: set[str]) -> float:
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def compute_similarity(a: str, b: str) -> float:
    """Weighted token + character trigram Jaccard similarity in [0.0, 1.0]."""
    tok_sim = _jaccard(extract_normalized_tokens(a), extract_normalized_tokens(b))
    tri_sim = _jaccard(_char_trigrams(a), _char_trigrams(b))
    return round(0.65 * tok_sim + 0.35 * tri_sim, 3)
