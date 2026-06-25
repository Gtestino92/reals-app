package com.reals.app

import android.app.Application
import com.reals.app.di.AppContainer
import com.reals.app.notifications.NotificationHelper

class RealsApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        NotificationHelper.ensureChannels(this)
    }
}
