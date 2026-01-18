package com.wakita181009.cleanarchitecture.infrastructure.config

import io.r2dbc.spi.ConnectionFactory
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.reactive.TransactionalOperator

@Configuration
@EnableTransactionManagement
class JooqConfig(
    private val cfi: ConnectionFactory,
) {
    @Bean
    fun dsl(): DSLContext = DSL.using(cfi).dsl()

    @Bean
    fun transactionalOperator(): TransactionalOperator = TransactionalOperator.create(R2dbcTransactionManager(cfi))
}
