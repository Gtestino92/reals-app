package com.reals.app.data.api

interface AuthTokenProvider {
    suspend fun getIdToken(forceRefresh: Boolean): String
}
