package com.wakita181009.cleanarchitecture.framework.properties

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties("app")
@Validated
class AppProperties(
    @field:NotBlank
    val jiraApiToken: String,
)
