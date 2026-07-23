plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.mipush.diagnostics"
}

dependencies {
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":magisk-xposed-kit:diagnostics"))

    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}
