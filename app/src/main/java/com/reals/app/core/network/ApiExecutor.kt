package com.reals.app.core.network

import android.util.Log
import com.reals.app.data.dto.ErrorResponseDto
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

class ApiExecutor(private val json: Json) {
    suspend fun <T> execute(call: suspend () -> Response<T>): ApiResult<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Failure(ApiError.Unexpected("Respuesta exitosa sin body."))
                }
            } else {
                ApiResult.Failure(parseBackendError(response))
            }
        } catch (exception: IOException) {
            ApiResult.Failure(ApiError.Network(exception.message ?: "Fallo de red."))
        } catch (exception: SerializationException) {
            ApiResult.Failure(ApiError.Unexpected(exception.message ?: "No se pudo parsear la respuesta."))
        } catch (exception: Exception) {
            ApiResult.Failure(ApiError.Unexpected(exception.message ?: exception::class.java.simpleName))
        }
    }

    suspend fun executeUnit(call: suspend () -> Response<Unit>): ApiResult<Unit> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(parseBackendError(response))
            }
        } catch (exception: IOException) {
            ApiResult.Failure(ApiError.Network(exception.message ?: "Fallo de red."))
        } catch (exception: SerializationException) {
            ApiResult.Failure(ApiError.Unexpected(exception.message ?: "No se pudo parsear la respuesta."))
        } catch (exception: Exception) {
            ApiResult.Failure(ApiError.Unexpected(exception.message ?: exception::class.java.simpleName))
        }
    }

    private fun parseBackendError(response: Response<*>): ApiError.Backend {
        val rawBody = response.errorBody()?.string()
        val parsed = rawBody?.let { body ->
            runCatching { json.decodeFromString<ErrorResponseDto>(body) }.getOrNull()
        }
        Log.w(
            "RealsApi",
            "HTTP ${response.code()} code=${parsed?.code} error=${parsed?.error} message=${parsed?.message ?: response.message()}",
        )
        return ApiError.Backend(
            statusCode = response.code(),
            code = parsed?.code,
            error = parsed?.error,
            message = parsed?.message ?: rawBody ?: response.message().ifBlank { "HTTP ${response.code()}" },
        )
    }
}
