plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.mipush.manager.client"
}

dependencies {
    api(project(":manager:contract"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":magisk-xposed-kit:logging"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}
