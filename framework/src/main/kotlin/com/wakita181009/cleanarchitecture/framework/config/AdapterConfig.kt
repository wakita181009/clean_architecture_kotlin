package com.wakita181009.cleanarchitecture.framework.config

import com.wakita181009.cleanarchitecture.framework.properties.AppProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class AdapterConfig(
    private val appProperties: AppProperties,
) {
    @Bean
    open fun jiraApiToken() = appProperties.jiraApiToken
}
