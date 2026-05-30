plugins {
    id("mipush.android.library")
    id("mipush.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.magisk317.mipush.manager"

    defaultConfig {
        missingDimensionStrategy("version", "normal")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":settings"))
    implementation(project(":uikit"))
    implementation(project(":core"))
    implementation(project(":vendor"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.napier)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.palette)
    implementation(libs.androidx.startup.runtime)

    implementation(libs.markdown)
    implementation(libs.haze.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
}
