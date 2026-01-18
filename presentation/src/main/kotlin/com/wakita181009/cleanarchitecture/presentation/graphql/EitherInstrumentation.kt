package com.wakita181009.cleanarchitecture.presentation.graphql

import arrow.core.Either
import arrow.core.getOrElse
import graphql.execution.DataFetcherResult
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.schema.DataFetcher
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class EitherInstrumentation : SimplePerformantInstrumentation() {
    override fun instrumentDataFetcher(
        dataFetcher: DataFetcher<*>?,
        parameters: InstrumentationFieldFetchParameters?,
        state: InstrumentationState?,
    ): DataFetcher<*> {
        if (dataFetcher == null) {
            return DataFetcher { null }
        }
        return DataFetcher { environment ->
            when (val originalResult = dataFetcher.get(environment)) {
                is Either<*, *> -> {
                    processEitherResult(originalResult)
                }

                is CompletableFuture<*> -> {
                    originalResult.thenApply { result ->
                        if (result is Either<*, *>) {
                            processEitherResult(result)
                        } else {
                            result
                        }
                    }
                }

                else -> originalResult
            }
        }
    }

    private fun <R> processEitherResult(eitherResult: Either<*, R>): Any? =
        eitherResult
            .map { successData ->
                DataFetcherResult
                    .newResult<Any>()
                    .data(successData)
                    .build()
            }.getOrElse { error ->
                throw error as Throwable
            }
}
