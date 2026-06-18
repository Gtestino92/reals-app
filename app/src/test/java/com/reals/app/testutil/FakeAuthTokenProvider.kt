package com.reals.app.testutil

import com.reals.app.core.firebase.MissingFirebaseTokenException
import com.reals.app.core.firebase.MissingFirebaseUserException
import com.reals.app.data.api.AuthTokenProvider

class FakeAuthTokenProvider(
    var token: String = "test-token",
) : AuthTokenProvider {
    var calls: List<Boolean> = emptyList()
        private set
    var failure: Throwable? = null

    override suspend fun getIdToken(forceRefresh: Boolean): String {
        calls = calls + forceRefresh
        failure?.let { throw it }
        return token
    }

    fun failMissingUser() {
        failure = MissingFirebaseUserException()
    }

    fun failMissingToken() {
        failure = MissingFirebaseTokenException()
    }
}
