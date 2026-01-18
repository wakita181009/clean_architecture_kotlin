package com.wakita181009.cleanarchitecture.application.error

abstract class ApplicationError(
    message: String?,
    cause: Throwable? = null,
) : Exception(message, cause)
