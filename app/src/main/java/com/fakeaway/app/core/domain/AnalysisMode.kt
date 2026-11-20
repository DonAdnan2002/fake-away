package com.fakeaway.app.core.domain

/**
 * Execution mode describing which analysis layers contributed to a verdict.
 */
enum class AnalysisMode {
    OFFLINE_FORENSIC,
    OFFLINE_NEURAL_AND_FORENSIC,
    HYBRID_ONLINE
}
