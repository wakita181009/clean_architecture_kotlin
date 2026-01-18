package com.wakita181009.cleanarchitecture.application.error

sealed class TransactionError(
    message: String?,
    cause: Throwable?,
) : ApplicationError(message, cause) {
    class ExecutionFailed(
        message: String?,
        cause: Throwable?,
    ) : TransactionError(message, cause)
}
