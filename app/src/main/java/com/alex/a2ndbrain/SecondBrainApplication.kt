package com.alex.a2ndbrain

import android.app.Application
import com.alex.a2ndbrain.core.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SecondBrainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SecondBrainApplication)
            modules(appModule)
        }
    }
}
