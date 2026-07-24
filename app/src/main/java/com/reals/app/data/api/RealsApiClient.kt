package com.reals.app.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.reals.app.BuildConfig
import com.reals.app.core.appcheck.AppCheckInterceptor
import com.reals.app.core.appcheck.AppCheckTokenProvider
import java.util.concurrent.TimeUnit
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object RealsApiClient {
    @OptIn(ExperimentalSerializationApi::class)
    fun create(baseUrl: String, json: Json, appCheckTokenProvider: AppCheckTokenProvider?): RealsApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(createOkHttpClient(json, appCheckTokenProvider))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RealsApi::class.java)
    }

    internal fun createOkHttpClient(json: Json, appCheckTokenProvider: AppCheckTokenProvider?): OkHttpClient {
        val shouldLogNetwork = BuildConfig.DEBUG && BuildConfig.REALS_ENVIRONMENT != "prod"
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            redactHeader("X-Firebase-AppCheck")
            level = if (shouldLogNetwork) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (appCheckTokenProvider != null) {
                    addInterceptor(AppCheckInterceptor(appCheckTokenProvider, json))
                }
            }
            .addInterceptor(logging)
            .build()
    }
}
