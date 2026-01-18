package com.wakita181009.cleanarchitecture.infrastructure.adapter

import arrow.core.Either
import arrow.core.flatMap
import com.wakita181009.cleanarchitecture.application.error.TransactionError
import com.wakita181009.cleanarchitecture.application.port.TransactionExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Component
class TransactionExecutorImpl(
    private val transactionalOperator: TransactionalOperator,
) : TransactionExecutor {
    override suspend fun <T> executeInTransaction(block: suspend () -> Either<*, T>): Either<TransactionError, T> =
        Either
            .catch {
                transactionalOperator.executeAndAwait {
                    block()
                }
            }.mapLeft { e ->
                TransactionError.ExecutionFailed(
                    message = "Transaction execution failed: ${e.message}",
                    cause = e,
                )
            }.flatMap { result ->
                result.mapLeft { domainError ->
                    TransactionError.ExecutionFailed(
                        message = "Transaction execution failed due to domain error",
                        cause = domainError as? Throwable,
                    )
                }
            }
}
