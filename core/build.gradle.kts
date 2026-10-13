plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    android {
        namespace = "io.github.magisk317.mipush.runtime.core"
        compileSdk(project.magiskCompileSdk())
        minSdk = libs.versions.minSdk.get().toInt()
        withHostTest {
        }
    }

    sourceSets {
        commonMain.dependencies {
            // core stays free of kit project dependencies (reviewed DAG: zero edges).
            // Structured data is produced here; JSONL encoding, rate limiting and
            // redaction live in the kit logging facade (io.github.magisk317.xposed.logging).
            implementation(libs.kermit)
            implementation(libs.kotlinx.serialization.json)
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
