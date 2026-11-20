package com.fakeaway.app

import android.app.Application
import com.fakeaway.app.core.i18n.LocaleManager

class FakeAwayApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        LocaleManager.applyAppLocale(container.preferences.language.value)
    }
}
