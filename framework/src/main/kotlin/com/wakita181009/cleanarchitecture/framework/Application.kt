package com.wakita181009.cleanarchitecture.framework

import com.wakita181009.cleanarchitecture.framework.runner.SyncJobRunner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ConfigurationPropertiesScan("com.wakita181009.cleanarchitecture.framework.properties")
@ComponentScan(
    basePackages = [
        "com.wakita181009.cleanarchitecture.domain",
        "com.wakita181009.cleanarchitecture.application",
        "com.wakita181009.cleanarchitecture.infrastructure",
        "com.wakita181009.cleanarchitecture.presentation",
        "com.wakita181009.cleanarchitecture.framework",
    ],
)
open class Application

fun main(args: Array<String>) {
    val isJobMode = SyncJobRunner.isJobMode(args)
    SpringApplicationBuilder(Application::class.java)
        .web(if (isJobMode) WebApplicationType.NONE else WebApplicationType.REACTIVE)
        .run(*args)
}
