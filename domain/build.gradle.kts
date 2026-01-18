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
}
