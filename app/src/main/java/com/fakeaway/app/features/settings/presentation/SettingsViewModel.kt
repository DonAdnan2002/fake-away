package com.fakeaway.app.features.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fakeaway.app.core.domain.AppLanguage
import com.fakeaway.app.core.domain.DispatcherProvider
import com.fakeaway.app.core.i18n.LocaleManager
import com.fakeaway.app.core.onnx.ModelDownloadProgress
import com.fakeaway.app.core.onnx.ModelSpec
import com.fakeaway.app.core.onnx.OnnxModelStore
import com.fakeaway.app.core.storage.AppPreferences
import com.fakeaway.app.features.fact_checker.data.TathabbatBackendClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface NewsroomConnectionStatus {
    data object Idle : NewsroomConnectionStatus
    data object Testing : NewsroomConnectionStatus
    data class Connected(val articles: Int, val claims: Int) : NewsroomConnectionStatus
    data object Failed : NewsroomConnectionStatus
}

data class SettingsUiState(
    val language: AppLanguage = AppLanguage.ARABIC,
    val strictOfflineMode: Boolean = false,
    val geminiKeyDraft: String = "",
    val tathabbatUrlDraft: String = "",
    val connectionStatus: NewsroomConnectionStatus = NewsroomConnectionStatus.Idle,
    val installedModels: Map<ModelSpec, Boolean> = emptyMap(),
    val activeDownloads: Map<ModelSpec, ModelDownloadProgress> = emptyMap(),
    val savedFeedbackCounter: Int = 0
)

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val modelStore: OnnxModelStore,
    private val tathabbatClient: TathabbatBackendClient? = null,
    private val dispatchers: DispatcherProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            language = preferences.language.value,
            strictOfflineMode = preferences.strictOfflineMode.value,
            geminiKeyDraft = preferences.geminiApiKey.value,
            tathabbatUrlDraft = preferences.tathabbatBaseUrl.value,
            installedModels = modelStore.installedStates.value
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val downloadJobs = mutableMapOf<ModelSpec, Job>()

    init {
        viewModelScope.launch {
            preferences.language.collect { lang ->
                _uiState.update { it.copy(language = lang) }
            }
        }
        viewModelScope.launch {
            preferences.strictOfflineMode.collect { offline ->
                _uiState.update { it.copy(strictOfflineMode = offline) }
            }
        }
        viewModelScope.launch {
            modelStore.installedStates.collect { map ->
                _uiState.update { it.copy(installedModels = map) }
            }
        }
    }

    fun selectLanguage(language: AppLanguage) {
        preferences.setLanguage(language)
        LocaleManager.applyAppLocale(language)
    }

    fun setStrictOfflineMode(enabled: Boolean) {
        preferences.setStrictOfflineMode(enabled)
    }

    fun updateGeminiKeyDraft(value: String) {
        _uiState.update { it.copy(geminiKeyDraft = value) }
    }

    fun saveGeminiKey() {
        preferences.setGeminiApiKey(_uiState.value.geminiKeyDraft)
        _uiState.update { it.copy(savedFeedbackCounter = it.savedFeedbackCounter + 1) }
    }

    fun updateTathabbatUrlDraft(value: String) {
        _uiState.update { it.copy(tathabbatUrlDraft = value, connectionStatus = NewsroomConnectionStatus.Idle) }
    }

    fun saveTathabbatUrl() {
        preferences.setTathabbatBaseUrl(_uiState.value.tathabbatUrlDraft)
        _uiState.update { it.copy(savedFeedbackCounter = it.savedFeedbackCounter + 1) }
    }

    fun testTathabbatConnection() {
        val url = _uiState.value.tathabbatUrlDraft.trim()
        preferences.setTathabbatBaseUrl(url)
        val client = tathabbatClient ?: return
        _uiState.update { it.copy(connectionStatus = NewsroomConnectionStatus.Testing) }
        viewModelScope.launch {
            val ioDisp = dispatchers?.io ?: Dispatchers.IO
            val outcome = withContext(ioDisp) {
                client.checkHealth(url)
            }
            outcome.fold(
                onSuccess = { health ->
                    _uiState.update {
                        it.copy(
                            connectionStatus = NewsroomConnectionStatus.Connected(
                                articles = health.articles,
                                claims = health.claims
                            )
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(connectionStatus = NewsroomConnectionStatus.Failed) }
                }
            )
        }
    }

    fun downloadModel(spec: ModelSpec) {
        if (downloadJobs[spec]?.isActive == true) return
        downloadJobs[spec] = viewModelScope.launch {
            modelStore.downloadModel(spec).collect { progress ->
                _uiState.update { state ->
                    val updated = state.activeDownloads.toMutableMap()
                    if (progress.completed || progress.error != null) {
                        updated.remove(spec)
                    } else {
                        updated[spec] = progress
                    }
                    state.copy(activeDownloads = updated)
                }
            }
        }
    }

    fun deleteModel(spec: ModelSpec) {
        downloadJobs.remove(spec)?.cancel()
        modelStore.deleteModel(spec)
    }
}
