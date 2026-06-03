package com.reals.app

import android.app.Application
import com.reals.app.di.AppContainer

class RealsApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
