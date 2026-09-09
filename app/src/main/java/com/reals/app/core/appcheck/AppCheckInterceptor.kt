package com.reals.app.core.appcheck

import com.reals.app.data.dto.ErrorResponseDto
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response

class AppCheckInterceptor(
    private val tokenProvider: AppCheckTokenProvider,
    private val json: Json,
    private val errorPeekBytes: Long = 64 * 1024,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val initialRequest = chain.request().withAppCheckToken(forceRefresh = false)
        val initialResponse = chain.proceed(initialRequest)

        if (!initialResponse.isInvalidAppCheckToken()) {
            return initialResponse
        }

        initialResponse.close()
        val retryRequest = chain.request()
            .newBuilder()
            .tag(AppCheckForcedRefreshRetry::class.java, AppCheckForcedRefreshRetry)
            .header(APP_CHECK_HEADER, tokenProvider.getToken(forceRefresh = true))
            .build()

        return chain.proceed(retryRequest)
    }

    private fun okhttp3.Request.withAppCheckToken(forceRefresh: Boolean): okhttp3.Request {
        return newBuilder()
            .header(APP_CHECK_HEADER, tokenProvider.getToken(forceRefresh))
            .build()
    }

    private fun Response.isInvalidAppCheckToken(): Boolean {
        if (request.tag(AppCheckForcedRefreshRetry::class.java) != null) return false
        if (code != 401) return false

        val parsed = runCatching {
            json.decodeFromString<ErrorResponseDto>(peekBody(errorPeekBytes).string())
        }.getOrNull()

        return parsed?.code == INVALID_APP_CHECK_TOKEN_CODE
    }

    private data object AppCheckForcedRefreshRetry

    companion object {
        const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
        const val INVALID_APP_CHECK_TOKEN_CODE = "INVALID_APP_CHECK_TOKEN"
    }
}

