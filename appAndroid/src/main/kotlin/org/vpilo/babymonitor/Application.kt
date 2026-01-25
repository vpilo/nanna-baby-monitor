package org.vpilo.babymonitor

import android.app.Application
import org.koin.android.ext.koin.androidContext

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeKoin {
            androidContext(this@Application)
        }
    }
}
