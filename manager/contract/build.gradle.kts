plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.mipush.manager.api"

    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(project(":core"))
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
