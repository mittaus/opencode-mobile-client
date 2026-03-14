package com.opencode.android

import android.app.Application
import com.opencode.android.di.allModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OpenCodeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OpenCodeApp)
            modules(allModules)
        }
    }
}
