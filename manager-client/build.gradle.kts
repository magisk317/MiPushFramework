plugins {
    id("magisk.android.library")
    alias(libs.plugins.robolectric.junit5)
}

android {
    namespace = "io.github.magisk317.mipush.manager.client"
}

dependencies {
    api(project(":manager-api"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.robolectric)
    testImplementation(libs.robolectric.junit5.extension)
    testRuntimeOnly(libs.junit.platform.launcher)
}
