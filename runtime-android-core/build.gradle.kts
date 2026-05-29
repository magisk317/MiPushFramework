plugins {
    id("mipush.android.library")
}

android {
    namespace = "io.github.magisk317.mipush.runtime.android"

    defaultConfig {
        buildConfigField("int", "RUNTIME_API_VERSION", "3")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":core"))
    implementation(project(":common"))
    implementation(libs.napier)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}
