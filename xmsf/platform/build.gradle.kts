plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    android {
        namespace = "io.github.magisk317.mipush.platform"
        compileSdk(project.magiskCompileSdk())
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":common"))
            implementation(project(":core"))
            implementation(project(":pinned"))
            implementation(project(":vendor"))
            implementation(project(":xmsf:runtime:store"))
            implementation(project(":magisk-xposed-kit:logging"))
            implementation(libs.anip.sdk)
            implementation(libs.androidx.documentfile)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.libsu.core)
            implementation(libs.palette)
        }
    }
}

dependencies {
    add("jvmTestImplementation", libs.junit.jupiter)
    add("jvmTestRuntimeOnly", libs.junit.platform.launcher)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
