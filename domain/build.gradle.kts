plugins {
    kotlin("jvm")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

dependencies {
    // ArrowKT
    api(libs.arrow.fx.coroutines)
    api(libs.arrow.resilience)
    // Logging
    api(libs.slf4j.api)
    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.arrow)
}

tasks.test {
    useJUnitPlatform()
}
