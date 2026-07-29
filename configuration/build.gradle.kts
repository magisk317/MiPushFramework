plugins {
    id("magisk.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.robolectric.junit5)
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
    testImplementation(libs.robolectric)
    testImplementation(libs.robolectric.junit5.extension)
    testRuntimeOnly(libs.junit.platform.launcher)
}
