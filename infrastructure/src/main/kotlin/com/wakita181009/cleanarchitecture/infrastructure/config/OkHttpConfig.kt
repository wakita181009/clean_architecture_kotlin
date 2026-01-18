package com.wakita181009.cleanarchitecture.infrastructure.config

import okhttp3.OkHttpClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OkHttpConfig {
    @Bean
    fun okHttpClient() = OkHttpClient.Builder().build()
}
