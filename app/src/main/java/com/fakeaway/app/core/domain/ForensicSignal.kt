package com.fakeaway.app.core.domain

/**
 * Individual forensic, neural, or corroboration signal contributing to a final verdict.
 */
data class ForensicSignal(
    val key: String,
    val labelKey: String,
    val score: Float,
    val detail: String,
    val isSuspicious: Boolean
)
