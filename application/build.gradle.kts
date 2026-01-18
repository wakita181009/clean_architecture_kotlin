plugins {
    kotlin("jvm")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

dependencies {
    api(project(":domain"))
    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.arrow)
}

tasks.test {
    useJUnitPlatform()
}
