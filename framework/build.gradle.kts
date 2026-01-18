plugins {
    kotlin("jvm")
    alias(libs.plugins.spring)
    alias(libs.plugins.spring.dependencies)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    application
}

dependencies {
    implementation(project(":infrastructure"))
    implementation(project(":presentation"))
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql")
    compileOnly("org.springframework.boot:spring-boot-devtools")
}

application {
    mainClass.set("com.wakita181009.cleanarchitecture.framework.ApplicationKt")
}
