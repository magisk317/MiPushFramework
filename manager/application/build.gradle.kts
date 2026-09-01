plugins {
    id("magisk.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.magisk317.mipush.manager.application"
}

dependencies {
    api(project(":manager:port"))
    api(project(":core"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
