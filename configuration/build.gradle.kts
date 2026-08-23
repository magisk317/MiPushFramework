plugins {
    id("magisk.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.magisk317.mipush.configuration"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":settings"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.documentfile)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}
