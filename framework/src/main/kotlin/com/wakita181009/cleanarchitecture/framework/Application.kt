package com.wakita181009.cleanarchitecture.framework

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
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
    runApplication<Application>(*args)
}
