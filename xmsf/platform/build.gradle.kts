plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.mipush.platform"
}

dependencies {
    implementation(project(":common"))
    implementation(libs.libsu.core)
    implementation(libs.palette)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
