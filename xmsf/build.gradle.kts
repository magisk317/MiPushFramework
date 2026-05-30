plugins {
    id("mipush.android.library")
    id("mipush.android.room")
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
    implementation(project(":core"))
    implementation(project(":settings"))
    implementation(project(":common"))
    implementation(project(":legacy"))
    implementation(project(":pinned"))

    implementation(libs.napier)
    implementation(libs.hyperisland.kit) {
        exclude(group = "androidx.appcompat", module = "appcompat")
        exclude(group = "com.google.android.material", module = "material")
    }
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.libsu.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.robolectric.junit5.extension)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.palette)
    implementation(libs.androidx.startup.runtime)

    implementation(libs.koin.android)
}
