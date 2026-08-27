import org.gradle.api.provider.Provider

plugins {
    id("magisk.android.application")
    id("magisk.app.signing")
    id("magisk.app.packaging")
    id("magisk.android.compose")
    alias(libs.plugins.kotlin.parcelize)
}

extra["artifactBaseName"] = "MiPush"

@Suppress("UNCHECKED_CAST")
val managerVersionName = (rootProject.extra["gitVersionName"] as Provider<String>).get()

android {
    namespace = "io.github.magisk317.mipush.app"

    flavorDimensions += "distribution"

    productFlavors {
        create("play") {
            dimension = "distribution"
        }
        create("github") {
            dimension = "distribution"
        }
    }

    defaultConfig {
        applicationId = rootProject.extra["APPLICATION_ID"] as String

        @Suppress("UNCHECKED_CAST")
        val verCode = (rootProject.extra["gitVersionCode"] as Provider<Int>).get()

        versionCode = verCode
        versionName = managerVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("version", "normal")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":manager:ui"))
    implementation(project(":manager:client"))
    implementation(project(":xposed"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
    implementation(libs.androidx.datastore.preferences)
    add("playImplementation", project(":magisk-ui-kit:billing"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
