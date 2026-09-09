package com.reals.app.core.appcheck

interface AppCheckTokenProvider {
    fun getToken(forceRefresh: Boolean): String
}

