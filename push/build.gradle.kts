plugins {
    id("mipush.android.application")
    id("mipush.android.room")
    alias(libs.plugins.hilt.android)
    id("mipush.android.compose")
    id("mipush.app.packaging")
    alias(libs.plugins.kotlin.serialization)
}

extra["mipushArtifactBaseName"] = "MiPushFramework"

val versionNameStr = rootProject.version.toString().ifBlank { libs.versions.versionName.get() }
val pushVersionCode = libs.versions.pushVersionCode.get().toInt()

android {
    namespace = "com.xiaomi.xmsf"

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.xiaomi.xmsf"
        versionCode = pushVersionCode
        versionName = versionNameStr

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64"))
        }

        buildConfigField("String", "GIT_TAG", "\"$versionNameStr\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            // Reduce debug APK size
            packaging {
                jniLibs {
                    useLegacyPackaging = false
                }
                dex {
                    useLegacyPackaging = false
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    jvmArgs("-Xshare:off")
    useJUnitPlatform()
}

dependencies {
    implementation(project(":common"))
    implementation(project(":uikit"))
    implementation(project(":legacy-runtime"))
    // push IS the MiPush SDK — 88 files in com.xiaomi.mipush.sdk.* directly import
    // com.xiaomi.xmpush.thrift.* (ActionType, ConfigKey, XmPushThriftSerializeUtils, etc.).
    // These are not transitively available through legacy-runtime (uses implementation,
    // not api), so push must depend on protocol-frozen directly. This is a pragmatic
    // boundary: the SDK implementation necessarily touches the protocol layer.
    implementation(project(":protocol-frozen"))

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

    implementation(libs.markdown)
    implementation(libs.haze.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
}
