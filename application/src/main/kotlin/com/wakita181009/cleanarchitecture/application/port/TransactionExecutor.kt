package com.wakita181009.cleanarchitecture.application.port

import arrow.core.Either
import com.wakita181009.cleanarchitecture.application.error.TransactionError

interface TransactionExecutor {
    suspend fun <T> executeInTransaction(block: suspend () -> Either<*, T>): Either<TransactionError, T>
}
