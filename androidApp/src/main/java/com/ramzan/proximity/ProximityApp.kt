package com.ramzan.proximity

import android.app.Application
import com.ramzan.proximity.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ProximityApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ProximityApp)
            modules(appModules)
        }
    }
}
