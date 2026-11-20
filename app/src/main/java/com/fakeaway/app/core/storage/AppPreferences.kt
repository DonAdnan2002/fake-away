package com.fakeaway.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.fakeaway.app.core.domain.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive settings store backed by local SharedPreferences.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "fakeaway_prefs",
        Context.MODE_PRIVATE
    )

    private val _language = MutableStateFlow(
        AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, AppLanguage.ARABIC.tag))
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _strictOfflineMode = MutableStateFlow(
        prefs.getBoolean(KEY_STRICT_OFFLINE, false)
    )
    val strictOfflineMode: StateFlow<Boolean> = _strictOfflineMode.asStateFlow()

    private val _geminiApiKey = MutableStateFlow(
        prefs.getString(KEY_GEMINI_KEY, "").orEmpty()
    )
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _tathabbatBaseUrl = MutableStateFlow(
        prefs.getString(KEY_TATHABBAT_URL, "").orEmpty()
    )
    val tathabbatBaseUrl: StateFlow<String> = _tathabbatBaseUrl.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
        _language.value = language
    }

    fun setStrictOfflineMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STRICT_OFFLINE, enabled).apply()
        _strictOfflineMode.value = enabled
    }

    fun setGeminiApiKey(apiKey: String) {
        val cleaned = apiKey.trim()
        prefs.edit().putString(KEY_GEMINI_KEY, cleaned).apply()
        _geminiApiKey.value = cleaned
    }

    fun setTathabbatBaseUrl(url: String) {
        val cleaned = url.trim().trimEnd('/')
        prefs.edit().putString(KEY_TATHABBAT_URL, cleaned).apply()
        _tathabbatBaseUrl.value = cleaned
    }

    companion object {
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_STRICT_OFFLINE = "strict_offline_mode"
        private const val KEY_GEMINI_KEY = "gemini_api_key"
        private const val KEY_TATHABBAT_URL = "tathabbat_base_url"
    }
}
