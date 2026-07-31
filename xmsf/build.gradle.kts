plugins {
    id("magisk.android.library")
    id("magisk.android.room")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.robolectric.junit5)
}

extra["artifactBaseName"] = "xmsf"

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
        aidl = true
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
        val buildTs = project.findProperty("buildTs")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val fullVersionName = if (buildTs != null) "$versionNameStr-$buildTs" else versionNameStr
        buildConfigField("String", "VERSION_NAME", "\"$fullVersionName\"")
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
    jvmArgs(
        "-Xshare:off",
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
        "-Xmx4g",
    )
    useJUnitPlatform()
    // Prevent Robolectric native runtime races within and across XMSF flavor tests.
    maxParallelForks = 1
    forkEvery = 1
}

tasks.configureEach {
    if (name == "testVc105DebugUnitTest") {
        mustRunAfter("testNormalDebugUnitTest")
    }
}

dependencies {
    implementation(project(":manager-api"))
    implementation(project(":diagnostics"))
    implementation(project(":core"))
    implementation(project(":settings"))
    implementation(project(":common"))
    implementation(project(":configuration"))
    implementation(project(":vendor"))
    implementation(project(":pinned"))
    implementation(project(":magisk-xposed-kit"))

    implementation(libs.napier)
    implementation(libs.hyperisland.kit) {
        exclude(group = "androidx.appcompat", module = "appcompat")
        exclude(group = "com.google.android.material", module = "material")
    }
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.libsu.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.robolectric.junit5.extension)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.palette)
    implementation(libs.androidx.startup.runtime)

    implementation(libs.koin.android)
}
