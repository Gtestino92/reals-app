package com.reals.app.data.api

import com.reals.app.core.appcheck.AppCheckFailureReason
import com.reals.app.core.appcheck.AppCheckInterceptor
import com.reals.app.core.appcheck.AppCheckTokenAcquisitionException
import com.reals.app.testutil.FakeAppCheckTokenProvider
import com.reals.app.testutil.testJson
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCheckInterceptorTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `adds app check header using cached token first`() {
        server.enqueue(jsonResponse(200, """{"status":"ok"}"""))
        val provider = FakeAppCheckTokenProvider(mutableListOf("cached-token"))

        client(provider).newCall(request("/api/ping")).execute().close()

        val recorded = server.takeRequest()
        assertEquals("cached-token", recorded.getHeader(AppCheckInterceptor.APP_CHECK_HEADER))
        assertEquals(listOf(false), provider.calls)
    }

    @Test
    fun `never places app check token in url`() {
        server.enqueue(jsonResponse(200, """{"status":"ok"}"""))
        val provider = FakeAppCheckTokenProvider(mutableListOf("secret-app-check-token"))

        client(provider).newCall(request("/api/ping?existing=true")).execute().close()

        val recorded = server.takeRequest()
        assertFalse(recorded.requestUrl.toString().contains("secret-app-check-token"))
        assertEquals("true", recorded.requestUrl?.queryParameter("existing"))
    }

    @Test
    fun `token provider failure prevents http call`() {
        val provider = FakeAppCheckTokenProvider()
        provider.failure = AppCheckTokenAcquisitionException(
            reason = AppCheckFailureReason.TOKEN_UNAVAILABLE,
            message = "temporary failure",
        )

        val thrown = runCatching {
            client(provider).newCall(request("/api/ping")).execute()
        }.exceptionOrNull()

        assertTrue(thrown is AppCheckTokenAcquisitionException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `invalid app check token retries once with forced refresh`() {
        server.enqueue(appCheckError(401, "INVALID_APP_CHECK_TOKEN"))
        server.enqueue(jsonResponse(200, """{"status":"ok"}"""))
        val provider = FakeAppCheckTokenProvider(mutableListOf("cached-token", "forced-token"))

        val response = client(provider).newCall(request("/api/ping")).execute()

        assertEquals(200, response.code)
        response.close()
        assertEquals(listOf(false, true), provider.calls)
        assertEquals("cached-token", server.takeRequest().getHeader(AppCheckInterceptor.APP_CHECK_HEADER))
        assertEquals("forced-token", server.takeRequest().getHeader(AppCheckInterceptor.APP_CHECK_HEADER))
    }

    @Test
    fun `second invalid app check token response is returned without another retry`() {
        server.enqueue(appCheckError(401, "INVALID_APP_CHECK_TOKEN"))
        server.enqueue(appCheckError(401, "INVALID_APP_CHECK_TOKEN"))
        val provider = FakeAppCheckTokenProvider(mutableListOf("cached-token", "forced-token"))

        val response = client(provider).newCall(request("/api/ping")).execute()
        val body = response.body!!.string()

        assertEquals(401, response.code)
        assertTrue(body.contains("INVALID_APP_CHECK_TOKEN"))
        assertEquals(listOf(false, true), provider.calls)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `missing app check token is not retried and body remains readable`() {
        server.enqueue(appCheckError(401, "MISSING_APP_CHECK_TOKEN"))
        val provider = FakeAppCheckTokenProvider(mutableListOf("cached-token"))

        val response = client(provider).newCall(request("/api/ping")).execute()
        val body = response.body!!.string()

        assertEquals(401, response.code)
        assertTrue(body.contains("MISSING_APP_CHECK_TOKEN"))
        assertEquals(listOf(false), provider.calls)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `verification unavailable is not retried`() {
        server.enqueue(appCheckError(503, "APP_CHECK_VERIFICATION_UNAVAILABLE"))
        val provider = FakeAppCheckTokenProvider(mutableListOf("cached-token"))

        val response = client(provider).newCall(request("/api/ping")).execute()

        assertEquals(503, response.code)
        response.close()
        assertEquals(listOf(false), provider.calls)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `ordinary firebase auth rejection is not retried by app check interceptor`() {
        server.enqueue(appCheckError(401, "INVALID_TOKEN"))
        val provider = FakeAppCheckTokenProvider(mutableListOf("cached-token"))

        val response = client(provider).newCall(request("/api/ping")).execute()

        assertEquals(401, response.code)
        response.close()
        assertEquals(listOf(false), provider.calls)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `client without app check provider does not request token or send header`() {
        server.enqueue(jsonResponse(200, """{"status":"ok"}"""))

        val response = RealsApiClient.createOkHttpClient(testJson, null)
            .newCall(request("/api/ping"))
            .execute()

        assertEquals(200, response.code)
        response.close()
        val recorded = server.takeRequest()
        assertEquals(null, recorded.getHeader(AppCheckInterceptor.APP_CHECK_HEADER))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `client with app check provider preserves header injection`() {
        server.enqueue(jsonResponse(200, """{"status":"ok"}"""))
        val provider = FakeAppCheckTokenProvider(mutableListOf("cached-token"))

        val response = RealsApiClient.createOkHttpClient(testJson, provider)
            .newCall(request("/api/ping"))
            .execute()

        assertEquals(200, response.code)
        response.close()
        assertEquals("cached-token", server.takeRequest().getHeader(AppCheckInterceptor.APP_CHECK_HEADER))
        assertEquals(listOf(false), provider.calls)
    }

    @Test
    fun `app check header remains redacted from network logging`() {
        val apiClientSource = File("src/main/java/com/reals/app/data/api/RealsApiClient.kt").readText()

        assertTrue(apiClientSource.contains("""redactHeader("X-Firebase-AppCheck")"""))
    }

    private fun client(provider: FakeAppCheckTokenProvider): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AppCheckInterceptor(provider, testJson))
            .build()

    private fun request(path: String): Request =
        Request.Builder()
            .url(server.url(path))
            .build()

    private fun appCheckError(statusCode: Int, code: String): MockResponse =
        jsonResponse(statusCode, """{"code":"$code","error":"$code","message":"backend error"}""")

    private fun jsonResponse(statusCode: Int, body: String): MockResponse =
        MockResponse()
            .setResponseCode(statusCode)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
}

