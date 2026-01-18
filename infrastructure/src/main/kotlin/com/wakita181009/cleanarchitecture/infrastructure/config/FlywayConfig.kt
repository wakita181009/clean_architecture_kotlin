package com.wakita181009.cleanarchitecture.infrastructure.config

import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.flyway.FlywayProperties
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableConfigurationProperties(R2dbcProperties::class, FlywayProperties::class)
@Profile("!test")
class FlywayConfig {
    @Bean(initMethod = "migrate")
    fun flyway(
        flywayProperties: FlywayProperties,
        r2dbcProperties: R2dbcProperties,
    ): Flyway =
        Flyway
            .configure()
            .dataSource(
                flywayProperties.url,
                r2dbcProperties.username,
                r2dbcProperties.password,
            ).baselineOnMigrate(true)
            .load()
}
