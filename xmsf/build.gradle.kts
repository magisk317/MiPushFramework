plugins {
    id("mipush.android.library")
    id("mipush.android.room")
    id("mipush.android.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.robolectric.junit5)
}

extra["mipushArtifactBaseName"] = "xmsf"

val versionNameStr = rootProject.version.toString().ifBlank { libs.versions.versionName.get() }
val pushVersionCode = libs.versions.pushVersionCode.get().toInt()

android {
    namespace = "com.xiaomi.xmsf"

    sourceSets {
        getByName("main") {
            assets.directories.add(rootProject.layout.projectDirectory.dir("xposed/src/main/compat").asFile.path)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    flavorDimensions += "version"

    productFlavors {
        create("normal") {
            dimension = "version"
        }
        create("vc105") {
            dimension = "version"
        }
    }

    defaultConfig {
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64"))
        }

        buildConfigField("String", "GIT_TAG", "\"$versionNameStr\"")
        buildConfigField("String", "VERSION_NAME", "\"$versionNameStr\"")
    }

    buildTypes {
        debug {
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    jvmArgs("-Xshare:off", "--enable-native-access=ALL-UNNAMED")
    useJUnitPlatform()
}

dependencies {
    implementation(project(":common"))
    implementation(project(":uikit"))
    implementation(project(":legacy"))
    implementation(project(":pinned"))
    compileOnly(project(":protocol"))

    implementation(libs.napier)
    implementation(libs.hyperisland.kit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.libsu.core)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.robolectric.junit5.extension)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.palette)
    implementation(libs.androidx.startup.runtime)

    implementation(libs.markdown)
    implementation(libs.haze.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
}
