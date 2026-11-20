from app.core.text_normalizer import compute_similarity, extract_normalized_tokens, normalize_text


def test_arabic_normalization_handles_hamza_ta_marbuta_diacritics_and_al() -> None:
    tokens_a = extract_normalized_tokens("الإنْفِجَارُ فِي المَوصِل")
    tokens_b = extract_normalized_tokens("انفجار في موصل")
    assert "انفجار" in tokens_a
    assert "انفجار" in tokens_b
    assert compute_similarity("الإنفجار في الموصل اليوم", "انفجار في موصل اليوم") > 0.6


def test_kurdish_sorani_normalization_matches_variants() -> None:
    norm = normalize_text("بەپەلە ڕاگەیاندنی کوردی لە هەولێر")
    assert "ه" in norm
    sim = compute_similarity("بەپەلە ڕاگەیاندنی کوردی لە هەولێر", "بهپهله راگهیاندني كوردي له ههولير")
    assert sim >= 0.45


def test_unrelated_claims_have_low_similarity() -> None:
    sim = compute_similarity(
        "ارتفاع أسعار النفط في الأسواق العالمية",
        "نتائج امتحانات السادس الإعدادي في العراق",
    )
    assert sim < 0.15
