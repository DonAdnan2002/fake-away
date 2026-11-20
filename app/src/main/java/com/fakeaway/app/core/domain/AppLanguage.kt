package com.fakeaway.app.core.domain

/**
 * Supported UI, OCR, and Fact-Check languages with layout direction metadata.
 */
enum class AppLanguage(
    val tag: String,
    val displayName: String,
    val isRtl: Boolean
) {
    ARABIC(tag = "ar", displayName = "العربية", isRtl = true),
    ENGLISH(tag = "en", displayName = "English", isRtl = false),
    KURDISH_SORANI(tag = "ckb", displayName = "کوردی سۆرانی", isRtl = true);

    companion object {
        fun fromTag(tag: String?): AppLanguage = when {
            tag == null -> ARABIC
            tag.startsWith("ar", ignoreCase = true) -> ARABIC
            tag.startsWith("ckb", ignoreCase = true) || tag.startsWith("ku", ignoreCase = true) -> KURDISH_SORANI
            tag.startsWith("en", ignoreCase = true) -> ENGLISH
            else -> ARABIC
        }
    }
}
