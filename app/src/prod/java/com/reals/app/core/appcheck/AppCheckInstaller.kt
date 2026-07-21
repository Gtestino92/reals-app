package com.reals.app.core.appcheck

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
    }
}

