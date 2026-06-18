package com.reals.app.testutil

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult

fun <T> ApiResult<T>.successValue(): T = when (this) {
    is ApiResult.Success -> value
    is ApiResult.Failure -> throw AssertionError("Expected success but got $error")
}

fun ApiResult<*>.failureError(): ApiError = when (this) {
    is ApiResult.Success -> throw AssertionError("Expected failure but got $value")
    is ApiResult.Failure -> error
}
