package com.fakeaway.app.features.fact_checker.presentation

import com.fakeaway.app.features.fact_checker.domain.FactCheckResult

data class FactCheckUiState(
    val inputText: String = "",
    val isVerifying: Boolean = false,
    val isFacebookLinkBanner: Boolean = false,
    val result: FactCheckResult? = null,
    val emptyError: Boolean = false,
    val isEscalating: Boolean = false,
    val escalatedClaimId: Long? = null,
    val escalationError: String? = null
)
