import org.gradle.api.provider.Provider

plugins {
    id("mipush.android.application")
    id("mipush.app.packaging")
    alias(libs.plugins.kotlin.parcelize)
}

extra["mipushArtifactBaseName"] = "MiPush"

android {
    namespace = "io.github.magisk317.mipush.app"

    defaultConfig {
        applicationId = rootProject.extra["APPLICATION_ID"] as String

        @Suppress("UNCHECKED_CAST")
        val verName = (rootProject.extra["gitVersionName"] as Provider<String>).get()
        @Suppress("UNCHECKED_CAST")
        val verCode = (rootProject.extra["gitVersionCode"] as Provider<Int>).get()

        versionCode = verCode
        versionName = verName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    api(project(":common"))
    api(project(":xposed"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
