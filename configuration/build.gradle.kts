plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    android {
        namespace = "io.github.magisk317.mipush.configuration"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
        }
        androidMain.dependencies {
            implementation(project(":common"))
            implementation(project(":settings"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.documentfile)
        }
    }
}

dependencies {
    add("jvmTestImplementation", libs.junit.jupiter)
    add("jvmTestImplementation", libs.mockk)
    add("jvmTestRuntimeOnly", libs.junit.platform.launcher)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
