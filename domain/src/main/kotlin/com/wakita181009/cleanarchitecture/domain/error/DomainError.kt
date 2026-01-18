package com.wakita181009.cleanarchitecture.domain.error

abstract class DomainError(
    message: String?,
    cause: Throwable?,
) : Exception(message, cause)
