plugins {
    id("mipush.android.library")
    id("mipush.android.room")
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.magisk317.mipush.runtime.core"

    defaultConfig {
        buildConfigField("int", "RUNTIME_API_VERSION", "3")
    }

    buildTypes {
        release {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":protocol"))
    implementation(project(":legacy"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.napier)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockkery.runtime.jvm)
    testRuntimeOnly(libs.junit.platform.launcher)
}
