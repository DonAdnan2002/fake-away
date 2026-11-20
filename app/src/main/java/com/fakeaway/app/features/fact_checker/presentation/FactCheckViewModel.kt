package com.fakeaway.app.features.fact_checker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.domain.DispatcherProvider
import com.fakeaway.app.features.fact_checker.data.TathabbatBackendClient
import com.fakeaway.app.features.fact_checker.domain.VerifyClaimUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FactCheckViewModel(
    private val verifyClaimUseCase: VerifyClaimUseCase,
    private val currentLanguage: () -> AppLanguage,
    private val tathabbatClient: TathabbatBackendClient? = null,
    private val tathabbatUrlProvider: () -> String = { "" },
    private val dispatchers: DispatcherProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(FactCheckUiState())
    val uiState: StateFlow<FactCheckUiState> = _uiState.asStateFlow()

    fun updateInput(text: String, isFacebookLink: Boolean = false) {
        _uiState.update {
            it.copy(
                inputText = text,
                isFacebookLinkBanner = isFacebookLink,
                emptyError = false
            )
        }
    }

    fun clear() {
        _uiState.value = FactCheckUiState()
    }

    fun loadSample(language: AppLanguage) {
        val sample = when (language) {
            AppLanguage.ARABIC ->
                "عاجل جداً!! شاهد قبل الحذف: تسريب خطير ومفاجأة صادمة للجميع، انشرها فوراً قبل إغلاق الصفحة!!!"
            AppLanguage.KURDISH_SORANI ->
                "بەپەلە و گرنگ!! پێش سڕینەوە بیبینە: باوەڕ ناکەیت نهێنی گەورە ئاشکرا بوو، بڵاوی بکەنەوە پێش سڕینەوەی!!!"
            AppLanguage.ENGLISH ->
                "SHOCKING!! Watch before deleted: Secret they hide from everyone, forward to everyone 100% free giveaway!!!"
        }
        updateInput(sample)
    }

    fun verify(textOverride: String? = null, isFacebookLink: Boolean = false) {
        val target = (textOverride ?: _uiState.value.inputText).trim()
        if (target.isEmpty()) {
            _uiState.update { it.copy(emptyError = true) }
            return
        }
        _uiState.update {
            it.copy(
                inputText = target,
                isFacebookLinkBanner = isFacebookLink || it.isFacebookLinkBanner,
                isVerifying = true,
                emptyError = false,
                escalatedClaimId = null,
                escalationError = null
            )
        }
        viewModelScope.launch {
            val outcome = verifyClaimUseCase(target, currentLanguage())
            outcome.fold(
                onSuccess = { res ->
                    _uiState.update { it.copy(isVerifying = false, result = res) }
                },
                onFailure = {
                    _uiState.update { it.copy(isVerifying = false, emptyError = true) }
                }
            )
        }
    }

    fun escalateToNewsroom() {
        val res = _uiState.value.result ?: return
        val client = tathabbatClient ?: return
        val baseUrl = tathabbatUrlProvider().trim()
        if (baseUrl.isEmpty()) {
            _uiState.update { it.copy(escalationError = "NO_SERVER_URL") }
            return
        }

        _uiState.update { it.copy(isEscalating = true, escalationError = null) }
        viewModelScope.launch {
            val ioDispatcher = dispatchers?.io ?: kotlinx.coroutines.Dispatchers.IO
            val outcome = withContext(ioDispatcher) {
                client.escalateToNewsroom(
                    baseUrl = baseUrl,
                    text = res.originalText,
                    language = res.detectedLanguage,
                    offlineRiskScore = res.offlineRiskScore,
                    scanType = "NEWS_TEXT",
                    summary = res.explanation
                )
            }
            outcome.fold(
                onSuccess = { id ->
                    _uiState.update { it.copy(isEscalating = false, escalatedClaimId = id) }
                },
                onFailure = {
                    _uiState.update { it.copy(isEscalating = false, escalationError = "REQUEST_FAILED") }
                }
            )
        }
    }
}
