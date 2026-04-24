plugins {
    id("mipush.android.library")
    id("mipush.android.room")
    alias(libs.plugins.hilt.android)
    id("mipush.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

extra["mipushArtifactBaseName"] = "MiPushRuntime"

android {
    namespace = "io.github.magisk317.mipush.runtime"

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":legacy"))
    implementation(project(":protocol"))

    implementation(libs.napier)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.libsu.core)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockkery.runtime.jvm)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.palette)
    implementation(libs.androidx.startup.runtime)

    implementation(libs.haze.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
