import org.gradle.api.provider.Provider

plugins {
    id("mipush.android.library")
    id("mipush.android.room")
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.magisk317.mipush.common"

    defaultConfig {
        @Suppress("UNCHECKED_CAST")
        val gitVersionName = (rootProject.extra["gitVersionName"] as Provider<String>).get()
        @Suppress("UNCHECKED_CAST")
        val gitVersionCode = (rootProject.extra["gitVersionCode"] as Provider<Int>).get()

        buildConfigField("String", "APPLICATION_ID", "\"${rootProject.extra["APPLICATION_ID"]}\"")
        buildConfigField("String", "VERSION_NAME", "\"$gitVersionName\"")
        buildConfigField("int", "VERSION_CODE", "$gitVersionCode")
        buildConfigField("String", "PUSH_VERSION_CODE", "\"${libs.versions.versionCode.get()}\"")
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
    implementation(project(":pinned"))
    compileOnly(project(":protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)
    implementation(libs.napier)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockkery.runtime.jvm)
    testRuntimeOnly(libs.junit.platform.launcher)
}
