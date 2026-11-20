package com.fakeaway.app.core.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.fakeaway.app.core.domain.AppLanguage
import java.util.Locale

/**
 * Applies trilingual (Arabic / English / Kurdish Sorani) locales cleanly to both
 * AppCompat Activities and foreground overlay Services.
 */
object LocaleManager {

    fun applyAppLocale(language: AppLanguage) {
        val locales = LocaleListCompat.forLanguageTags(language.tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * Produces a localized Context wrapper for background Services (such as FloatingShieldService)
     * that do not automatically recreate when AppCompat locales change.
     */
    fun localizedContext(base: Context, language: AppLanguage): Context {
        val locale = Locale.forLanguageTag(language.tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
