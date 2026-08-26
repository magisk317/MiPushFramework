plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "io.github.magisk317.mipush.runtime.core"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        withHostTest {
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":xmsf:runtime:store"))
            implementation(libs.kermit)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

dependencies {
    add("androidHostTestImplementation", libs.junit.jupiter)
    add("androidHostTestImplementation", libs.mockk)
    add("androidHostTestRuntimeOnly", libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
