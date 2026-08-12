import org.gradle.api.tasks.testing.Test

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

tasks.withType<Test>().configureEach {
    jvmArgs(
        "-Xshare:off",
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.security=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "-XX:+EnableDynamicAgentLoading",
        "-Xmx4g",
    )
    useJUnitPlatform()
    // The Robolectric JUnit 5 extension creates SDK-specific sandboxes. Running
    // those sandboxes concurrently can make ZipFS reopen the same Android font
    // archive and fail with FileSystemAlreadyExistsException.
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
    // Isolate each test class; Robolectric's native runtime is not safe to tear
    // down and recreate between SDK sandboxes on the CI JDK.
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
    testImplementation(libs.conscrypt.openjdk.uber)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.palette)
    implementation(libs.androidx.startup.runtime)

    implementation(libs.koin.android)
}
