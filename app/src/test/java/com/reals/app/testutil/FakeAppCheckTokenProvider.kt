package com.reals.app.testutil

import com.reals.app.core.appcheck.AppCheckTokenProvider

class FakeAppCheckTokenProvider(
    private val tokens: MutableList<String> = mutableListOf("app-check-token"),
) : AppCheckTokenProvider {
    var calls: List<Boolean> = emptyList()
        private set
    var failure: Throwable? = null

    override fun getToken(forceRefresh: Boolean): String {
        calls = calls + forceRefresh
        failure?.let { throw it }
        return if (tokens.isNotEmpty()) tokens.removeAt(0) else "app-check-token"
    }
}

