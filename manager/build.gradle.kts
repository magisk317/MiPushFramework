plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
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
    implementation(project(":manager-client"))
    implementation(project(":settings"))
    api(project(":magisk-ui-kit"))
    implementation(project(":core"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.napier)
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.palette)
    implementation(libs.androidx.startup.runtime)

    implementation(libs.markdown)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
}
