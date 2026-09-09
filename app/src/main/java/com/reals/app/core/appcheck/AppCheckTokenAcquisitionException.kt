package com.reals.app.core.appcheck

import java.io.IOException

class AppCheckTokenAcquisitionException(
    val reason: AppCheckFailureReason,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

enum class AppCheckFailureReason {
    NOT_CONFIGURED,
    TOKEN_UNAVAILABLE,
}

