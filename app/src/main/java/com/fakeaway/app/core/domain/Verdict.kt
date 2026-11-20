package com.fakeaway.app.core.domain

/**
 * Classification verdict produced by any Fake Away detector slice.
 */
enum class Verdict {
    REAL,
    SUSPICIOUS,
    FAKE,
    UNVERIFIED;

    companion object {
        fun fromFakeScore(fakeScore: Float): Verdict = when {
            fakeScore >= 0.65f -> FAKE
            fakeScore >= 0.38f -> SUSPICIOUS
            else -> REAL
        }
    }
}

/**
 * Modality type persisted in local scan history.
 */
enum class ScanType {
    VOICE,
    SCREEN,
    IMAGE,
    NEWS_TEXT
}
