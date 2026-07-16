package com.reals.app.data.repository

import com.reals.app.core.firebase.FirebaseNotConfiguredException
import com.reals.app.core.firebase.MissingFirebaseTokenException
import com.reals.app.core.firebase.MissingFirebaseUserException
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiExecutor
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.AuthFailureReason
import com.reals.app.data.api.AuthTokenProvider
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import retrofit2.Response

abstract class AuthenticatedRepository(
    private val tokenProvider: AuthTokenProvider,
    private val apiExecutor: ApiExecutor,
) {
    protected suspend fun <T> authorizedCall(
        call: suspend (authorization: String) -> Response<T>,
    ): ApiResult<T> {
        val firstHeader = authorizationHeader(forceRefresh = false)
        if (firstHeader is ApiResult.Failure) return firstHeader

        val first = apiExecutor.execute { call((firstHeader as ApiResult.Success).value) }
        if (first.shouldRefreshToken()) {
            val refreshedHeader = authorizationHeader(forceRefresh = true)
            if (refreshedHeader is ApiResult.Failure) return refreshedHeader
            return apiExecutor.execute { call((refreshedHeader as ApiResult.Success).value) }
        }
        return first
    }

    protected suspend fun authorizedUnitCall(
        call: suspend (authorization: String) -> Response<Unit>,
    ): ApiResult<Unit> {
        val firstHeader = authorizationHeader(forceRefresh = false)
        if (firstHeader is ApiResult.Failure) return firstHeader

        val first = apiExecutor.executeUnit { call((firstHeader as ApiResult.Success).value) }
        if (first.shouldRefreshToken()) {
            val refreshedHeader = authorizationHeader(forceRefresh = true)
            if (refreshedHeader is ApiResult.Failure) return refreshedHeader
            return apiExecutor.executeUnit { call((refreshedHeader as ApiResult.Success).value) }
        }
        return first
    }

    private suspend fun authorizationHeader(forceRefresh: Boolean): ApiResult<String> {
        return try {
            ApiResult.Success("Bearer ${tokenProvider.getIdToken(forceRefresh)}")
        } catch (exception: FirebaseNotConfiguredException) {
            ApiResult.Failure(
                ApiError.Auth(
                    reason = AuthFailureReason.FIREBASE_NOT_CONFIGURED,
                    message = exception.message ?: "Firebase no está configurado.",
                ),
            )
        } catch (exception: MissingFirebaseUserException) {
            ApiResult.Failure(
                ApiError.Auth(
                    reason = AuthFailureReason.NOT_SIGNED_IN,
                    message = exception.message ?: "No hay usuario autenticado.",
                ),
            )
        } catch (exception: MissingFirebaseTokenException) {
            ApiResult.Failure(
                ApiError.Auth(
                    reason = AuthFailureReason.TOKEN_MISSING,
                    message = exception.message ?: "No se pudo obtener el token de Firebase.",
                ),
            )
        } catch (exception: FirebaseAuthInvalidUserException) {
            ApiResult.Failure(
                ApiError.Auth(
                    reason = AuthFailureReason.NOT_SIGNED_IN,
                    message = exception.message ?: "La sesión de Firebase ya no es válida.",
                ),
            )
        } catch (exception: Exception) {
            ApiResult.Failure(
                ApiError.Auth(
                    reason = AuthFailureReason.TOKEN_UNAVAILABLE,
                    message = exception.message ?: "No se pudo obtener el token de Firebase.",
                ),
            )
        }
    }

    private fun ApiResult<*>.shouldRefreshToken(): Boolean {
        val failure = this as? ApiResult.Failure ?: return false
        val backend = failure.error as? ApiError.Backend ?: return false
        return backend.statusCode == 401
    }
}
