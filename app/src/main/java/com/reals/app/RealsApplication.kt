package com.reals.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.reals.app.BuildConfig
import com.reals.app.core.appcheck.AppCheckInstaller
import com.reals.app.di.AppContainer
import com.reals.app.notifications.NotificationHelper

class RealsApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val firebaseApp = runCatching {
            FirebaseApp.getApps(this).firstOrNull() ?: FirebaseApp.initializeApp(this)
        }.getOrNull()
        if (firebaseApp != null && BuildConfig.ENABLE_FIREBASE_APP_CHECK) {
            AppCheckInstaller.install()
        }
        appContainer = AppContainer(this)
        NotificationHelper.ensureChannels(this)
    }
}
