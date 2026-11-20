package com.fakeaway.app.features.fact_checker.domain

import com.fakeaway.app.core.domain.AppLanguage

/**
 * Abstraction for verifying claims across Arabic, English, and Kurdish Sorani.
 */
interface FactCheckEngine {
    suspend fun verify(rawText: String, uiLanguage: AppLanguage): FactCheckResult
}
