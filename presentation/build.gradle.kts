plugins {
    kotlin("jvm")
    alias(libs.plugins.spring)
    alias(libs.plugins.spring.dependencies)
    alias(libs.plugins.graphql)
    alias(libs.plugins.ktlint)
}

dependencies {
    api(project(":application"))
    // Spring Boot
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-webflux")
    // GraphQL Kotlin
    implementation(libs.graphql.kotlin.spring.server)
    implementation(libs.graphql.kotlin.hooks.provider)
    implementation("com.graphql-java:graphql-java-extended-scalars:24.0")
}
// Disable spring boot jar task
tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    enabled = false
}
val graphqlGenerateSDL by tasks.getting(com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateSDLTask::class) {
    packages.set(listOf("com.wakita181009.cleanarchitecture.presentation.graphql"))
    schemaFile.set(file("${project.projectDir}/src/main/resources/schema.graphql"))
}
